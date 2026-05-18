package com.moai.backend.domain.flipped.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.flipped.dto.*;
import com.moai.backend.domain.flipped.entity.AiInteraction;
import com.moai.backend.domain.flipped.entity.FlippedSession;
import com.moai.backend.domain.flipped.repository.AiInteractionRepository;
import com.moai.backend.domain.flipped.repository.FlippedSessionRepository;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlippedLearningService {

    private final LearningRoomRepository learningRoomRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final FlippedSessionRepository flippedSessionRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final UserRepository userRepository;
    private final LlmService llmService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PlatformTransactionManager transactionManager;

    // Redis 키 접두사
    private static final String REDIS_PREFIX = "moai:flipped:";
    // 세션 상태 TTL (1시간) — 세션 만료 안전장치
    private static final Duration SESSION_TTL = Duration.ofHours(1);

    /**
     * 거꾸로 학습 세션을 시작한다.
     * 1) 학습실·주차 소유권 검증
     * 2) sessionId(UUID) 발급
     * 3) 첫 번째 키워드 기반 LLM 첫 안내 문구 생성
     * 4) AI 대화 이력(role=assistant) 저장
     * 5) Redis 세션 상태 초기화 (keywordIndex=0, exchangeCount=0)
     */
    @Transactional
    public FlippedStartResponseDto startSession(String email, String roomId,
                                                 FlippedStartRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        WeeklyCurriculum curriculum = weeklyCurriculumRepository
                .findByIdAndRoomId(requestDto.getCurriculumId(), roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // 영상 시청 완료(진척도 40% 이상) 여부 검증
        if (curriculum.getCompletionRate().compareTo(new BigDecimal("40")) < 0) {
            throw new CustomException(ErrorCode.FLIPPED_VIDEO_NOT_COMPLETED);
        }

        // 중복 세션 검사
        if (flippedSessionRepository.findByUserIdAndCurriculumId(user.getId(), curriculum.getId()).isPresent()) {
            throw new CustomException(ErrorCode.FLIPPED_SESSION_ALREADY_COMPLETED);
        }

        String sessionId = UUID.randomUUID().toString();

        List<String> keywords = curriculum.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            // 키워드가 없으면 topic을 단일 키워드로 사용
            keywords = Collections.singletonList(curriculum.getTopic());
        }

        // 첫 번째 키워드만 대상으로 안내 문구 생성
        String firstKeyword = keywords.get(0);
        String firstMessage = generateFirstMessage(firstKeyword, room.getSubject(), room.getLevel());

        AiInteraction interaction = AiInteraction.builder()
                .sessionId(sessionId)
                .user(user)
                .room(room)
                .curriculum(curriculum)
                .role("assistant")
                .content(firstMessage)
                .isCounterQuestion(false)
                .build();

        aiInteractionRepository.save(interaction);

        // Redis 세션 상태 초기화: 키워드 인덱스 0, 교환 횟수 0
        initializeSessionState(sessionId);

        return new FlippedStartResponseDto(sessionId, firstMessage);
    }

    /**
     * Redis에 거꾸로 학습 세션 상태를 초기화한다.
     * - keywordIndex: 현재 진행 중인 키워드 인덱스 (0부터 시작)
     * - exchangeCount: 현재 키워드에 대한 사용자-AI 교환 횟수
     */
    private void initializeSessionState(String sessionId) {
        String keywordIndexKey = REDIS_PREFIX + sessionId + ":keywordIndex";
        String exchangeCountKey = REDIS_PREFIX + sessionId + ":exchangeCount";

        redisTemplate.opsForValue().set(keywordIndexKey, "0", SESSION_TTL);
        redisTemplate.opsForValue().set(exchangeCountKey, "0", SESSION_TTL);
    }

    /**
     * LLM을 호출하여 첫 번째 키워드에 대한 안내 문구를 생성한다.
     */
    private String generateFirstMessage(String keyword, String subject, String level) {
        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 거꾸로 학습 AI입니다.

                학습 주제: %s (수준: %s)

                역할: 학생이 첫 번째 키워드를 스스로 설명하도록 유도하는 세션 시작 안내를 3문장 이내로 작성합니다.

                규칙:
                - 마크다운 문법(**볼드**, - 불릿 등) 절대 사용 금지. 화면에 기호가 그대로 노출됩니다.
                - 줄 바꿈으로 환영 인사와 키워드 제시를 분리하세요.
                - 키워드를 별도 줄에 명확하게 제시하세요.
                - 키워드나 핵심 개념을 언급할 때는 반드시 작은따옴표('')로 감싸세요. 예: '접속사'에 대해 설명해 주시겠어요?
                - 정답이나 힌트를 절대 먼저 설명하지 마세요.
                - 제어 태그([COUNTER_QUESTION] 등) 사용 금지.
                - 따뜻하고 간결한 한국어 존댓말. 3문장 이내.
                """, subject, level);

        String userMessage = String.format(
                "첫 번째 키워드: '%s'\n\n위 키워드로 학생이 설명을 시작하도록 유도하는 첫 안내 문구를 작성하세요.",
                keyword
        );

        return llmService.call(new LlmRequestDto(systemPrompt, userMessage)).getContent();
    }

    // ── 5-5: 평가 결과 조회 ──

    /**
     * 거꾸로 학습 평가 결과와 전체 대화 기록을 조회한다.
     */
    public FlippedResultResponseDto getResult(String email, String roomId, String sessionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        FlippedSession session = flippedSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.FLIPPED_SESSION_NOT_FOUND));

        // 대화 이력을 시간순으로 조회하여 ConversationItem 리스트로 변환
        List<FlippedResultResponseDto.ConversationItem> conversations =
                aiInteractionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                        .map(ai -> new FlippedResultResponseDto.ConversationItem(
                                ai.getRole(), ai.getContent()))
                        .toList();

        return new FlippedResultResponseDto(
                session.getScore(),
                session.getGainedKeywords(),
                session.getWeakKeywords(),
                session.getFeedback(),
                conversations
        );
    }

    // ── 5-4: 세션 종료 + 최종 평가 ──

    // 거꾸로 학습 완료 시 주차 진척도에 더할 비율
    private static final BigDecimal FLIPPED_COMPLETION_WEIGHT = new BigDecimal("30.00");
    private static final BigDecimal MAX_COMPLETION = new BigDecimal("100.00");

    /**
     * 거꾸로 학습 세션을 종료하고 최종 평가를 수행한다.
     *
     * 처리 순서:
     * 1. 대화 이력 조회 → LLM 최종 평가 호출
     * 2. FlippedSession INSERT
     * 3. UserKeyword 처리:
     *    - gainedKeywords → 기존 weakness resolve + strength INSERT (없을 때만)
     *    - weakKeywords → 기존 weakness count 증가 또는 새 weakness INSERT
     * 4. 주차 진척도 +30% → 학습실 전체 진척도 재계산
     * 5. Redis 세션 상태 정리
     */
    @Transactional
    public FlippedEndResponseDto endSession(String email, String roomId,
                                             FlippedEndRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        String sessionId = requestDto.getSessionId();

        // 1. 전체 대화 이력 조회
        List<AiInteraction> history = aiInteractionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (history.isEmpty()) {
            throw new CustomException(ErrorCode.FLIPPED_SESSION_NOT_FOUND);
        }
        WeeklyCurriculum curriculum = history.get(0).getCurriculum();

        // 2. LLM 최종 평가 호출 — 대화 내용 기반 score/keywords/feedback/result 생성
        LlmFlippedEvaluationResult evaluation = evaluateSession(history, curriculum, room);

        // 커리큘럼 키워드 목록과 교집합 필터 — LLM이 목록 외 키워드를 반환하는 경우 방어
        List<String> curriculumKeywords = curriculum.getKeywords();
        if (curriculumKeywords != null && !curriculumKeywords.isEmpty()) {
            Set<String> allowed = new HashSet<>(curriculumKeywords);
            evaluation.setGainedKeywords(
                    evaluation.getGainedKeywords() != null
                            ? evaluation.getGainedKeywords().stream().filter(allowed::contains).toList()
                            : List.of());
            evaluation.setWeakKeywords(
                    evaluation.getWeakKeywords() != null
                            ? evaluation.getWeakKeywords().stream().filter(allowed::contains).toList()
                            : List.of());
        }

        // 3. FlippedSession 저장
        FlippedSession session = FlippedSession.builder()
                .sessionId(sessionId)
                .user(user)
                .room(room)
                .curriculum(curriculum)
                .flippedResult(evaluation.getFlippedResult())
                .score(evaluation.getScore())
                .feedback(evaluation.getFeedback())
                .gainedKeywords(evaluation.getGainedKeywords())
                .weakKeywords(evaluation.getWeakKeywords())
                .build();
        flippedSessionRepository.save(session);

        // 4. UserKeyword 처리
        // gainedKeywords: 기존 weakness resolve + strength INSERT (미존재 시)
        resolveAndPromoteKeywords(user, room, curriculum, evaluation.getGainedKeywords());
        // weakKeywords: 기존 weakness count 증가 또는 새 weakness INSERT
        upsertWeaknesses(user, room, curriculum, evaluation.getWeakKeywords());

        // 5. 주차 진척도 += 30%, 학습실 전체 진척도 재계산
        updateCompletionRates(curriculum, room);

        // 6. Redis 세션 상태 정리
        cleanupSessionState(sessionId);

        return new FlippedEndResponseDto(
                sessionId,
                evaluation.getFlippedResult(),
                evaluation.getScore(),
                evaluation.getGainedKeywords(),
                evaluation.getWeakKeywords(),
                evaluation.getFeedback()
        );
    }

    /**
     * LLM을 호출하여 거꾸로 학습 대화를 최종 평가한다.
     * JSON 형태로 score, gainedKeywords, weakKeywords, feedback, flippedResult를 반환받는다.
     */
    private LlmFlippedEvaluationResult evaluateSession(List<AiInteraction> history,
                                                        WeeklyCurriculum curriculum,
                                                        LearningRoom room) {
        String conversationText = history.stream()
                .map(h -> String.format("[%s]: %s", h.getRole(), h.getContent()))
                .collect(Collectors.joining("\n"));

        String keywordList = (curriculum.getKeywords() != null)
                ? String.join(", ", curriculum.getKeywords())
                : curriculum.getTopic();

        String subjectInfo = (room != null)
                ? String.format("학습 주제: %s (수준: %s)\n", room.getSubject(), room.getLevel())
                : "";

        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 거꾸로 학습 메타인지 평가 전문가 AI입니다.

                %s학생과 AI 튜터의 전체 대화 기록을 종합해 학생의 이해도를 평가합니다.

                ■ 각 키워드별 채점 (내부 분석용 — JSON 출력에 포함하지 않음):
                각 키워드에 대해 학생이 말한 내용의 정확성과 풍부성을 종합하여 0~100점으로 채점한다.

                · 정확성: 말한 내용이 실제로 맞는가
                  - 완전히 틀리거나 주제와 무관하면 0점
                  - 막연하게만 언급하면 낮은 점수 (예: "접속할 때 쓰는 말" → 20점대)
                  - 핵심을 정확히 설명하면 높은 점수

                · 풍부성: 설명이 얼마나 구체적이고 충분한가 (기준을 너무 높게 잡지 않을 것)
                  - 짧더라도 핵심을 잘 짚은 설명이면 충분히 가점
                  - 긴 설명을 요구하지 않는다

                채점 기준 예시 (접속사의 경우):
                  - "접속할 때 쓰는 말" → 20점 (막연한 언급, 정확성 부족)
                  - "단어나 문장을 연결해주는 말" → 45점 (방향은 맞지만 짧고 단순)
                  - "and, but처럼 단어나 절을 이어주는 품사" → 70점 (정확하고 구체적)
                  - "단어·구·절을 논리적으로 연결하며 대등·종속 관계를 나타내는 품사" → 90점

                ■ gainedKeywords / weakKeywords 분류:
                - gainedKeywords: 키워드 점수 60점 이상
                - weakKeywords: 60점 미만 (설명 없음, 틀림, 막연한 언급 포함)

                ■ 전체 score 산정:
                - score = 모든 키워드 점수의 단순 평균 (소수점 버림, 0~100 정수)
                - 예) 키워드 5개, 각각 70/45/20/0/0점 → score = 27점
                - 참여·노력·시도에 대한 점수 가산 절대 금지

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                {
                  "score": 0~100 (정수, 키워드 점수 평균),
                  "flippedResult": "pass", "partial", 또는 "fail" (60점 이상 pass / 30~59점 partial / 30점 미만 fail),
                  "gainedKeywords": ["점수 60점 이상인 키워드만"],
                  "weakKeywords": ["점수 60점 미만인 키워드 전부"],
                  "feedback": "구조화된 피드백 (한국어, 마크다운 허용). 각 항목 사이에 빈 줄(\\n\\n)을 넣을 것:\\n\\n✅ 잘한 점: 정확하게 설명한 내용 핵심 요약 (1~2문장)\\n\\n⚠️ 보완할 점: 틀린 내용 교정 (없으면 생략)\\n\\n💡 핵심 정리: 부족하거나 누락된 내용 보강 제안 (1~2문장)"
                }

                ■ 필수 규칙:
                1. 틀린 내용은 반드시 피드백에서 올바르게 바로잡을 것.
                2. feedback에 격려 문구를 넣어도 되지만, 격려가 score에 영향을 줘서는 안 된다.
                3. gainedKeywords와 weakKeywords에는 반드시 아래 목록에 있는 키워드만 사용. 임의 생성/변경 금지, 목록 원문 그대로 반환.
                4. 대상 키워드 목록: [%s]
                """, subjectInfo, keywordList);

        String userMessage = String.format(
                "주차 키워드: [%s]\n\n대화 내용:\n%s",
                keywordList, conversationText
        );

        return llmService.callJson(
                new LlmRequestDto(systemPrompt, userMessage),
                LlmFlippedEvaluationResult.class
        );
    }

    /**
     * gainedKeywords에 대한 UserKeyword 처리:
     * 1. 해당 키워드의 기존 weakness가 있으면 resolve (is_resolved=true, resolved_at=now)
     * 2. 해당 키워드의 strength 레코드가 없으면 새로 INSERT
     */
    private void resolveAndPromoteKeywords(User user, LearningRoom room,
                                            WeeklyCurriculum curriculum,
                                            List<String> gainedKeywords) {
        if (gainedKeywords == null || gainedKeywords.isEmpty()) return;

        for (String keyword : gainedKeywords) {
            // 기존 weakness가 있으면 해소 처리
            userKeywordRepository
                    .findByUserIdAndRoomIdAndKeywordAndKeywordType(
                            user.getId(), room.getId(), keyword, "weakness")
                    .ifPresent(uk -> {
                        if (!uk.getIsResolved()) {
                            uk.resolve();
                        }
                    });

            // strength 레코드가 없으면 새로 생성
            boolean strengthExists = userKeywordRepository
                    .findByUserIdAndRoomIdAndKeywordAndKeywordType(
                            user.getId(), room.getId(), keyword, "strength")
                    .isPresent();

            if (!strengthExists) {
                UserKeyword strength = UserKeyword.builder()
                        .user(user)
                        .room(room)
                        .curriculum(curriculum)
                        .keyword(keyword)
                        .keywordType("strength")
                        .build();
                userKeywordRepository.save(strength);
            }
        }
    }

    /**
     * weakKeywords를 UserKeyword에 UPSERT한다.
     * - 이미 weakness로 존재하면 weaknessCount 증가
     * - 존재하지 않으면 새 weakness INSERT
     */
    private void upsertWeaknesses(User user, LearningRoom room, WeeklyCurriculum curriculum,
                                   List<String> weakKeywords) {
        if (weakKeywords == null || weakKeywords.isEmpty()) return;

        for (String keyword : weakKeywords) {
            Optional<UserKeyword> existing = userKeywordRepository
                    .findByUserIdAndRoomIdAndKeywordAndKeywordType(
                            user.getId(), room.getId(), keyword, "weakness");

            if (existing.isPresent()) {
                existing.get().incrementWeaknessCount();
            } else {
                UserKeyword newKeyword = UserKeyword.builder()
                        .user(user)
                        .room(room)
                        .curriculum(curriculum)
                        .keyword(keyword)
                        .keywordType("weakness")
                        .build();
                userKeywordRepository.save(newKeyword);
            }
        }
    }

    /**
     * 주차 진척도에 거꾸로 학습 완료 비율(30%)을 더하고,
     * 학습실 전체 진척도를 전체 주차 평균으로 재계산한다.
     */
    private void updateCompletionRates(WeeklyCurriculum curriculum, LearningRoom room) {
        BigDecimal newRate = curriculum.getCompletionRate().add(FLIPPED_COMPLETION_WEIGHT);
        if (newRate.compareTo(MAX_COMPLETION) > 0) {
            newRate = MAX_COMPLETION;
        }
        curriculum.updateCompletionRate(newRate);

        List<WeeklyCurriculum> allWeeks = weeklyCurriculumRepository
                .findByRoomIdOrderByWeekNumber(room.getId());
        BigDecimal average = allWeeks.stream()
                .map(WeeklyCurriculum::getCompletionRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allWeeks.size()), 2, RoundingMode.HALF_UP);

        room.updateCompletionRate(average);
    }

    /**
     * 세션 종료 후 Redis에 저장된 세션 상태 키를 정리한다.
     */
    private void cleanupSessionState(String sessionId) {
        redisTemplate.delete(redisKey(sessionId, "keywordIndex"));
        redisTemplate.delete(redisKey(sessionId, "exchangeCount"));
        redisTemplate.delete(redisKey(sessionId, "reQuestionMode"));
        redisTemplate.delete(redisKey(sessionId, "completed"));
    }

    // ── 5-3: SSE 스트리밍 채팅 ──

    // 키워드당 최소/최대 교환 횟수
    private static final int MIN_EXCHANGES_PER_KEYWORD = 1;
    private static final int MAX_EXCHANGES_PER_KEYWORD = 3;

    // LLM 태그
    private static final String TAG_COUNTER_QUESTION = "[COUNTER_QUESTION]";
    private static final String TAG_NEXT_KEYWORD = "[NEXT_KEYWORD]";

    private final ObjectMapper objectMapper;

    /**
     * 거꾸로 학습 SSE 스트리밍 채팅을 처리한다.
     *
     * DB 커넥션 점유 방지를 위해 @Transactional을 사용하지 않고,
     * PROPAGATION_REQUIRES_NEW TransactionTemplate으로 개별 DB 작업만 짧은 독립 트랜잭션으로 처리한다.
     * (클래스 레벨 readOnly=true 트랜잭션에 합류하면 쓰기가 무시되므로 반드시 REQUIRES_NEW 필요)
     *
     * 흐름:
     * [트랜잭션 1 - REQUIRES_NEW] 사용자 메시지 저장 (즉시 커밋)
     * [트랜잭션 2 - REQUIRES_NEW] 대화 이력 조회 + LLM 컨텍스트 구성
     * [트랜잭션 없음] Redis 교환 횟수 + LLM 스트리밍 (커넥션 미점유)
     * [트랜잭션 3 - REQUIRES_NEW] 스트리밍 완료 후 AI 응답 저장
     */
    public void streamChat(String email, String roomId,
                           FlippedStreamRequestDto requestDto, SseEmitter emitter) {

        // 세션 완료 후 추가 메시지 차단
        if ("true".equals(redisTemplate.opsForValue().get(redisKey(requestDto.getSessionId(), "completed")))) {
            try {
                sendSseEvent(emitter, "session_complete", "모든 키워드에 대한 확인이 완료되었습니다. 이제 최종 평가를 받을 수 있습니다.");
                sendSseEvent(emitter, "done", "completed");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return;
        }

        // 클래스 레벨 @Transactional(readOnly=true)의 읽기 전용 트랜잭션에 합류하지 않도록
        // REQUIRES_NEW로 독립 트랜잭션을 생성한다.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // StreamContext: 트랜잭션 바깥에서 사용할 데이터를 모아서 반환하는 임시 구조체
        record StreamContext(
                User user, LearningRoom room, WeeklyCurriculum curriculum,
                String sessionId, List<Map<String, Object>> contents,
                List<String> keywords, int keywordIndex, long exchangeCount,
                String systemPrompt,
                boolean afterReQuestion  // 직전 AI 응답이 재질문이었는지 여부
        ) {}

        // ── 트랜잭션 1: 사용자 메시지 저장 (즉시 커밋 — SSE 실패와 무관하게 보존) ──
        record SavedContext(
                User user, LearningRoom room, WeeklyCurriculum curriculum,
                String sessionId, List<String> keywords, String topic
        ) {}

        SavedContext saved = tx.execute(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

            String sessionId = requestDto.getSessionId();

            // 세션 존재 확인 + curriculum 조회
            List<AiInteraction> existing = aiInteractionRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);
            if (existing.isEmpty()) {
                throw new CustomException(ErrorCode.FLIPPED_SESSION_NOT_FOUND);
            }
            WeeklyCurriculum curriculum = existing.get(0).getCurriculum();

            // Lazy 프록시 초기화 — TX1 세션이 살아있는 동안 keywords/topic을 즉시 로드
            List<String> keywords = curriculum.getKeywords();
            String topic = curriculum.getTopic();

            // 사용자 메시지 즉시 저장 — 이 트랜잭션이 커밋되면 메시지 유실 없음
            AiInteraction userInteraction = AiInteraction.builder()
                    .sessionId(sessionId)
                    .user(user)
                    .room(room)
                    .curriculum(curriculum)
                    .role("user")
                    .content(requestDto.getMessage())
                    .isCounterQuestion(false)
                    .build();
            aiInteractionRepository.save(userInteraction);

            return new SavedContext(user, room, curriculum, sessionId, keywords, topic);
        });

        // ── 트랜잭션 2: 대화 이력 로드 + LLM 컨텍스트 구성 (읽기 전용) ──
        StreamContext ctx = tx.execute(status -> {
            String sessionId = saved.sessionId();

            // 사용자 메시지 포함된 전체 이력 다시 조회 (트랜잭션 1에서 커밋 완료된 상태)
            List<AiInteraction> history = aiInteractionRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);
            List<Map<String, Object>> contents = buildGeminiContents(history);

            // Redis에서 키워드 인덱스 조회 + 교환 횟수 증가
            String keywordIndexKey = redisKey(sessionId, "keywordIndex");
            String exchangeCountKey = redisKey(sessionId, "exchangeCount");
            int keywordIndex = getRedisInt(keywordIndexKey);
            long exchangeCount = redisTemplate.opsForValue().increment(exchangeCountKey);
            redisTemplate.expire(exchangeCountKey, SESSION_TTL);

            List<String> keywords = saved.keywords();
            if (keywords == null || keywords.isEmpty()) {
                keywords = Collections.singletonList(saved.topic());
            }

            // 직전 AI 응답이 재질문이었는지 확인 (키워드당 1회 재질문 제한 관리)
            String reQuestionKey = redisKey(sessionId, "reQuestionMode");
            boolean afterReQuestion = "true".equals(redisTemplate.opsForValue().get(reQuestionKey));

            String systemPrompt = buildStreamSystemPrompt(keywords, keywordIndex, (int) exchangeCount, afterReQuestion, saved.room().getSubject(), saved.room().getLevel());

            return new StreamContext(saved.user(), saved.room(), saved.curriculum(),
                    sessionId, contents, keywords, keywordIndex, exchangeCount, systemPrompt, afterReQuestion);
        });

        if (ctx.afterReQuestion()) {
            try {
                String baseResponse = "알겠습니다.";
                String transitionMessage = transitionDisplayMessage(ctx.keywordIndex(), ctx.keywords());
                String contentToSave = appendAssistantMessage(baseResponse, transitionMessage);

                AiInteraction savedAssistant = tx.execute(status -> {
                    AiInteraction assistantInteraction = AiInteraction.builder()
                            .sessionId(ctx.sessionId())
                            .user(ctx.user())
                            .room(ctx.room())
                            .curriculum(ctx.curriculum())
                            .role("assistant")
                            .content(contentToSave)
                            .isCounterQuestion(false)
                            .build();
                    return aiInteractionRepository.save(assistantInteraction);
                });

                sendSseEvent(emitter, "token", baseResponse);
                handleKeywordTransition(ctx.sessionId(), ctx.keywordIndex(), ctx.keywords(), emitter);
                sendSseEvent(emitter, "done", savedAssistant.getId());
                emitter.complete();
            } catch (Exception e) {
                log.error("재질문 이후 강제 전환 처리 중 오류: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
            return;
        }

        if (isLowInformationAnswer(requestDto.getMessage())) {
            try {
                String currentKeyword = ctx.keywords().get(ctx.keywordIndex());
                String question = "'" + currentKeyword + "'에 대해 본인의 언어로 설명해 주시겠어요?";

                AiInteraction savedAssistant = tx.execute(status -> {
                    AiInteraction assistantInteraction = AiInteraction.builder()
                            .sessionId(ctx.sessionId())
                            .user(ctx.user())
                            .room(ctx.room())
                            .curriculum(ctx.curriculum())
                            .role("assistant")
                            .content(question)
                            .isCounterQuestion(true)
                            .build();
                    return aiInteractionRepository.save(assistantInteraction);
                });

                redisTemplate.opsForValue().set(redisKey(ctx.sessionId(), "reQuestionMode"), "true", SESSION_TTL);
                sendSseEvent(emitter, "counter_question", question);
                sendSseEvent(emitter, "done", savedAssistant.getId());
                emitter.complete();
            } catch (Exception e) {
                log.error("저정보 답변 재질문 처리 중 오류: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
            return;
        }

        // ── 트랜잭션 없음: LLM 스트리밍 (DB 커넥션 미점유) ──
        Flux<String> tokenFlux = llmService.callStream(ctx.systemPrompt(), ctx.contents());

        StringBuilder fullResponse = new StringBuilder();
        AtomicBoolean counterQuestionMode = new AtomicBoolean(false);
        AtomicBoolean nextKeywordDetected = new AtomicBoolean(false);
        AtomicReference<StringBuilder> tokenBuffer = new AtomicReference<>(new StringBuilder());

        tokenFlux
                .doOnNext(token -> {
                    fullResponse.append(token);
                    tokenBuffer.get().append(token);

                    String buffered = tokenBuffer.get().toString();
                    processTokenBuffer(buffered, tokenBuffer, counterQuestionMode,
                            nextKeywordDetected, (int) ctx.exchangeCount(), emitter);
                })
                .doOnComplete(() -> {
                    try {
                        String rawResponse = fullResponse.toString();
                        String cleanResponse = nextKeywordDetected.get()
                                ? cleanBeforeNextKeyword(rawResponse)
                                : cleanTags(rawResponse);
                        boolean isCounterQuestion = counterQuestionMode.get();
                        String currentKeyword = ctx.keywords().get(ctx.keywordIndex());

                        // ── 코드 레벨 강제 규칙 적용 ──
                        // LLM이 태그 규칙을 이행하지 않은 경우에도 화면에 반드시 질문이 남도록 보정한다.
                        // 프롬프트 지시만으로는 LLM이 규칙을 위반할 수 있으므로 반드시 코드로 보장한다.
                        boolean forcedTransition = false;
                        boolean saveAsCounterQuestion = isCounterQuestion;

                        if (isCounterQuestion) {
                            cleanResponse = ensureKeywordQuestion(cleanResponse, currentKeyword, requestDto.getMessage());
                        } else if (!nextKeywordDetected.get()) {
                            if (looksLikeQuestion(cleanResponse)) {
                                saveAsCounterQuestion = true;
                                cleanResponse = ensureKeywordQuestion(cleanResponse, currentKeyword, requestDto.getMessage());
                                log.warn("태그 없는 질문 응답 감지 → 재질문으로 처리");
                            } else {
                                forcedTransition = true;
                                log.warn("태그 없는 비질문 응답 감지 → 다음 키워드 질문으로 강제 전환");
                            }
                        }

                        boolean shouldTransition = !saveAsCounterQuestion
                                && (nextKeywordDetected.get() || forcedTransition
                                    || ctx.exchangeCount() >= MAX_EXCHANGES_PER_KEYWORD);

                        String transitionMessage = shouldTransition
                                ? transitionDisplayMessage(ctx.keywordIndex(), ctx.keywords())
                                : "";
                        String contentToSave = shouldTransition
                                ? appendAssistantMessage(cleanResponse, transitionMessage)
                                : cleanResponse;

                        // ── 트랜잭션 3: AI 응답 저장 ──
                        final boolean finalSaveAsCounterQuestion = saveAsCounterQuestion;
                        final String finalContentToSave = contentToSave;
                        AiInteraction savedAssistant = tx.execute(status -> {
                            AiInteraction assistantInteraction = AiInteraction.builder()
                                    .sessionId(ctx.sessionId())
                                    .user(ctx.user())
                                    .room(ctx.room())
                                    .curriculum(ctx.curriculum())
                                    .role("assistant")
                                    .content(finalContentToSave)
                                    .isCounterQuestion(finalSaveAsCounterQuestion)
                                    .build();
                            return aiInteractionRepository.save(assistantInteraction);
                        });

                        // 재질문 모드 관리: 정상 재질문(한도 내)인 경우에만 reQuestionMode를 설정한다.
                        if (saveAsCounterQuestion) {
                            redisTemplate.opsForValue().set(
                                    redisKey(ctx.sessionId(), "reQuestionMode"), "true", SESSION_TTL);
                        }

                        if (saveAsCounterQuestion) {
                            sendSseEvent(emitter, "counter_question", cleanResponse);
                        } else if (!cleanResponse.isBlank()) {
                            sendSseEvent(emitter, "token", cleanResponse);
                        }

                        if (shouldTransition) {
                            handleKeywordTransition(ctx.sessionId(), ctx.keywordIndex(),
                                    ctx.keywords(), emitter);
                        }

                        sendSseEvent(emitter, "done", savedAssistant.getId());
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("SSE 완료 처리 중 오류: {}", e.getMessage(), e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
                    log.error("LLM 스트리밍 오류: {}", error.getMessage(), error);
                    emitter.completeWithError(error);
                })
                .subscribe();
    }

    /**
     * 토큰 버퍼를 분석하여 태그만 감지한다.
     *
     * 태그 처리 규칙:
     * - [COUNTER_QUESTION]: 재질문 모드 플래그 설정
     * - [NEXT_KEYWORD]: 다음 키워드 전환 플래그 설정
     * 실제 SSE 전송은 응답 완료 시점에 서버 보정 규칙을 적용한 뒤 한 번만 수행한다.
     */
    private void processTokenBuffer(String buffered, AtomicReference<StringBuilder> tokenBuffer,
                                     AtomicBoolean counterQuestionMode,
                                     AtomicBoolean nextKeywordDetected,
                                     int exchangeCount, SseEmitter emitter) {
        // 응답은 완료 시점에 서버 규칙으로 확정해서 보낸다. 여기서는 태그 감지만 수행한다.
        if (counterQuestionMode.get() || nextKeywordDetected.get()) {
            return;
        }

        // 완성된 태그 처리
        if (buffered.contains(TAG_COUNTER_QUESTION)) {
            counterQuestionMode.set(true);
            tokenBuffer.set(new StringBuilder());
        } else if (buffered.contains(TAG_NEXT_KEYWORD)) {
            nextKeywordDetected.set(true);
            tokenBuffer.set(new StringBuilder());
        }
    }

    /**
     * 키워드 전환을 처리한다.
     * - 다음 키워드가 있으면 next_keyword 이벤트 전송 + Redis 상태 업데이트
     * - 마지막 키워드였으면 session_complete 이벤트 전송
     */
    private void handleKeywordTransition(String sessionId, int currentIndex,
                                          List<String> keywords, SseEmitter emitter) {
        int nextIndex = currentIndex + 1;
        String keywordIndexKey = redisKey(sessionId, "keywordIndex");
        String exchangeCountKey = redisKey(sessionId, "exchangeCount");

        // 키워드 전환 시 재질문 관련 상태 초기화
        redisTemplate.delete(redisKey(sessionId, "reQuestionMode"));

        if (nextIndex >= keywords.size()) {
            // 모든 키워드 완료 — Redis에 완료 플래그 기록하여 이후 메시지 차단
            redisTemplate.opsForValue().set(redisKey(sessionId, "completed"), "true", SESSION_TTL);
            sendSseEvent(emitter, "session_complete", transitionDisplayMessage(currentIndex, keywords));
        } else {
            // 다음 키워드로 전환: Redis 인덱스 증가, 교환 횟수 리셋
            redisTemplate.opsForValue().set(keywordIndexKey, String.valueOf(nextIndex), SESSION_TTL);
            redisTemplate.opsForValue().set(exchangeCountKey, "0", SESSION_TTL);

            // next_keyword 이벤트: 키워드 정보 + 학생에게 표시할 안내 문구 함께 전송
            String nextKeyword = keywords.get(nextIndex);
            String nextKeywordMessage = nextKeywordQuestion(nextKeyword);
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "next_keyword");
                payload.put("keyword", nextKeyword);
                payload.put("keywordIndex", nextIndex);
                payload.put("message", nextKeywordMessage);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
            } catch (IOException e) {
                log.error("next_keyword SSE 전송 실패: {}", e.getMessage());
            }
        }
    }

    private String transitionDisplayMessage(int currentIndex, List<String> keywords) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= keywords.size()) {
            return "모든 키워드에 대한 확인이 완료되었습니다. 이제 최종 평가를 받을 수 있습니다.";
        }
        return nextKeywordQuestion(keywords.get(nextIndex));
    }

    private String nextKeywordQuestion(String keyword) {
        return "다음 키워드: '" + keyword + "'\n이 용어에 대해 알고 계신 내용을 자유롭게 설명해 주시겠어요?";
    }

    private String appendAssistantMessage(String base, String next) {
        String left = base == null ? "" : cleanTags(base).trim();
        String right = next == null ? "" : next.trim();
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        if (left.contains(right)) return left;
        return left + "\n\n" + right;
    }

    private String cleanBeforeNextKeyword(String response) {
        if (response == null) return "";
        int nextTagIndex = response.indexOf(TAG_NEXT_KEYWORD);
        String visiblePart = nextTagIndex >= 0 ? response.substring(0, nextTagIndex) : response;
        return cleanTags(visiblePart);
    }

    private String ensureKeywordQuestion(String response, String keyword, String studentAnswer) {
        String clean = cleanTags(response).trim();
        String target = (keyword == null || keyword.isBlank()) ? "현재 키워드" : keyword.trim();
        boolean lowInformation = isLowInformationAnswer(studentAnswer);
        String fallback = lowInformation
                ? "'" + target + "'에 대해 본인의 언어로 설명해 주시겠어요?"
                : "'" + target + "'에서 방금 설명이 부족하거나 애매했던 핵심 역할, 특징, 예시 중 한 부분을 더 구체적으로 설명해 주시겠어요?";

        if (clean.isBlank()) return fallback;
        if (!looksLikeQuestion(clean)
                || isGenericReQuestion(clean)
                || (!lowInformation && isGenericWholeKeywordQuestion(clean, target))) {
            return fallback;
        }
        if (clean.contains(target)) return clean;
        return "'" + target + "'에서 " + clean;
    }

    private boolean isGenericReQuestion(String text) {
        if (text == null) return true;
        String normalized = cleanTags(text).replaceAll("\\s+", "");
        return normalized.equals("다시설명해주세요?")
                || normalized.equals("다시설명해주시겠어요?")
                || normalized.equals("설명해주시겠어요?")
                || normalized.equals("본인의언어로설명해주시겠어요?")
                || normalized.equals("본인의언어로다시설명해주시겠어요?")
                || normalized.contains("다시설명");
    }

    private boolean isGenericWholeKeywordQuestion(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) return false;
        String normalized = cleanTags(text).replaceAll("\\s+", "");
        String target = keyword.replaceAll("\\s+", "");
        if (!normalized.contains(target)) return false;

        boolean hasAspect =
                normalized.contains("부분")
                || normalized.contains("측면")
                || normalized.contains("역할")
                || normalized.contains("특징")
                || normalized.contains("예시")
                || normalized.contains("조건")
                || normalized.contains("구조")
                || normalized.contains("차이")
                || normalized.contains("이유")
                || normalized.contains("근거")
                || normalized.contains("정의")
                || normalized.contains("활용")
                || normalized.contains("사용")
                || normalized.contains("의미")
                || normalized.contains("관계")
                || normalized.contains("기능")
                || normalized.contains("원리")
                || normalized.contains("방식")
                || normalized.contains("핵심");

        return !hasAspect
                && (normalized.contains(target + "에대해")
                    || normalized.contains(target + "을")
                    || normalized.contains(target + "를"))
                && normalized.contains("설명");
    }

    private boolean isLowInformationAnswer(String message) {
        if (message == null) return true;
        String normalized = message.trim().replaceAll("\\s+", "");
        if (normalized.isBlank()) return true;
        if (normalized.length() <= 1) return true;
        if (normalized.matches("^[ㄱ-ㅎㅏ-ㅣ]+$")) return true;
        if (normalized.matches("^[a-zA-Z]$")) return true;
        String lower = normalized.toLowerCase();
        return lower.equals("몰라")
                || lower.equals("모름")
                || lower.contains("모르겠")
                || lower.equals("없음")
                || lower.equals("모릅니다");
    }

    private boolean looksLikeQuestion(String text) {
        if (text == null) return false;
        String normalized = cleanTags(text).trim();
        if (normalized.isBlank()) return false;
        return normalized.contains("?")
                || (normalized.endsWith("요")
                    && (normalized.contains("설명") || normalized.contains("말해") || normalized.contains("알려")));
    }

    /**
     * 시스템 프롬프트를 구성한다.
     * 키워드 목록, 현재 진행 키워드, 교환 횟수, 재질문 여부를 포함하여
     * LLM이 부족한 답변에 1회 재질문하고 마크다운으로 가독성 있게 응답하도록 안내한다.
     */
    private String buildStreamSystemPrompt(List<String> keywords, int keywordIndex,
                                            int exchangeCount, boolean afterReQuestion,
                                            String subject, String level) {
        String keywordList = String.join(", ", keywords);
        String currentKeyword = keywords.get(keywordIndex);

        String reQuestionSection;
        if (afterReQuestion) {
            reQuestionSection =
                "\n[이번 턴 필수 지시 — 절대 위반 불가]\n"
                + "재질문 이후 학생의 재답변을 받았습니다. 이 키워드 평가를 즉시 종료합니다.\n"
                + "① 응답은 '알겠습니다.' 또는 '확인했습니다.' 딱 한 문장만 작성\n"
                + "② 긍정 평가 문구('잘 하셨어요', '맞아요') 사용 금지\n"
                + "③ 키워드 정의·설명·개념 언급 절대 금지\n"
                + "④ [COUNTER_QUESTION] 태그 사용 절대 금지\n"
                + "⑤ 메타 발언 금지 ('가이드라인에 따라', '다음 키워드로 넘어갑니다' 등)\n"
                + "⑥ 응답 맨 끝에 [NEXT_KEYWORD] 반드시 붙일 것\n";
        } else {
            reQuestionSection =
                "\n[평가 기준 — 반드시 현재 키워드 '" + currentKeyword + "'에 대한 답변만 평가할 것]\n"
                + "이전 대화 기록에 등장한 다른 키워드를 현재 재질문에 사용하지 마세요.\n\n"
                + "학생 답변을 다음 두 가지로만 분류하세요:\n\n"
                + "▶ 정상 진행 → [NEXT_KEYWORD] 사용:\n"
                + "  학생이 '" + currentKeyword + "'의 의미·역할·특징을 본인 말로 설명한 경우\n"
                + "  응답 형식: 1~2문장 중립 확인 + [NEXT_KEYWORD]\n\n"
                + "▶ 재질문 → [COUNTER_QUESTION] 사용 (이 키워드에서 최대 1회):\n"
                + "  조건 A — 다음 중 하나라도 해당하면 반드시 재질문:\n"
                + "    · 의미 없는 단어/문자 ('ㄱ', 'ㅇ', '모르겠어요')\n"
                + "    · 주제와 무관한 내용\n"
                + "    · 키워드 이름에서 연상되는 단어만 나열하고 실제 개념을 설명하지 않은 경우\n"
                + "      예: '잘 활용하는 언어' (활용형 키워드에 대해), '연결할 때 쓰는 말' (접속사에 대해)\n"
                + "      → 이처럼 키워드의 의미·역할·특징을 실제로 설명하지 않은 경우는 조건 A 적용\n"
                + "    정확한 출력 형식: [COUNTER_QUESTION]'" + currentKeyword + "'에 대해 본인의 언어로 설명해 주시겠어요?\n"
                + "  조건 B — 설명은 했지만 틀렸거나, 핵심이 부족하거나, 애매한 경우:\n"
                + "    반드시 '" + currentKeyword + "'와 부족한 측면 1개를 함께 써서 질문하세요.\n"
                + "    부족한 측면은 핵심 역할, 특징, 예시, 사용 조건, 차이, 관계, 근거 중 실제 학생 답변에 맞는 하나를 고르세요.\n"
                + "    정확한 출력 형식: [COUNTER_QUESTION]'" + currentKeyword + "'에서 [부족한 측면] 부분을 더 구체적으로 설명해 주시겠어요?\n"
                + "    금지 출력: [COUNTER_QUESTION]다시 설명해 주시겠어요?\n"
                + "    금지 출력: [COUNTER_QUESTION]'" + currentKeyword + "'에 대해 다시 설명해 주시겠어요?\n"
                + "  ※ 재질문 텍스트에 키워드 정의·예시·힌트 절대 포함 금지\n"
                + "  ※ [COUNTER_QUESTION] 태그 없이 재설명을 요청하는 것 절대 금지 ('다시 설명해 주시겠어요?' 등을 태그 없이 출력 금지)\n\n"
                + "▶ 반드시 재질문해야 하는 입력 (이 경우 [NEXT_KEYWORD] 절대 금지):\n"
                + "  단일 자모 (ㄱ, ㅇ, ㄷ, ㄴ, ㅁ 등) / '모르겠어요' / 주제와 무관한 문장\n"
                + "  키워드 이름에서 연상되는 단어만 있고 개념 설명이 없는 경우\n";
        }

        return String.format("""
                당신은 MoAI 거꾸로 학습 AI입니다. 학생의 설명을 평가합니다.

                [학습 맥락]
                학습 주제: %s (수준: %s)

                [절대 금지 — 어떤 경우에도 위반 불가]
                1. 키워드 개념을 설명·정의·요약하는 것
                   예: "주어는 ~입니다", "활용이란 ~", "어간이 변하는 현상을 뜻하는"
                   [COUNTER_QUESTION] 텍스트 안에도 절대 금지
                2. [COUNTER_QUESTION] 태그 없이 재설명 요청 (태그 없는 '다시 설명해 주시겠어요?' 출력 금지)
                3. 마크다운 문법 (**볼드**, - 불릿, | 표 등)
                4. 3문장 초과 응답
                5. 불필요한 인사말·격려 ("감사합니다", "열심히 하세요")
                6. 메타 발언 ("가이드라인에 따라", "시스템상", "다음 키워드로 넘어갑니다")
                7. 한 번에 여러 질문
                8. 이전 키워드명을 현재 재질문에 사용하는 것 (현재 키워드: %s 만 사용)

                [필수 — 따옴표 규칙]
                질문에서 키워드나 핵심 개념을 언급할 때는 반드시 작은따옴표('')로 감싸세요.
                올바른 예: '%s'에 대해 본인의 언어로 설명해 주시겠어요?
                올바른 예: '%s'에서 핵심 역할 부분을 더 구체적으로 설명해 주시겠어요?
                잘못된 예: %s에 대해 설명해 주시겠어요?

                [전체 키워드 목록]
                %s

                [현재 진행 상태]
                현재 키워드: %s (인덱스 %d/%d) / 교환 횟수: %d회
                %s
                [태그 규칙]
                - [COUNTER_QUESTION]: 재질문 텍스트 바로 앞에만. 이 응답에서 [NEXT_KEYWORD] 동시 사용 금지.
                - [NEXT_KEYWORD]: 이 키워드 평가 완료 시 응답 맨 끝에만. 태그 뒤에 텍스트 없음.
                """,
                subject, level,
                currentKeyword,
                currentKeyword, currentKeyword, currentKeyword,
                keywordList, currentKeyword, keywordIndex + 1, keywords.size(),
                exchangeCount, reQuestionSection
        );
    }

    /**
     * AiInteraction 대화 이력을 Gemini API contents 배열로 변환한다.
     * Gemini는 role이 "user"와 "model"만 허용하므로 "assistant"→"model"로 변환.
     */
    private List<Map<String, Object>> buildGeminiContents(List<AiInteraction> history) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiInteraction interaction : history) {
            // Gemini API는 "model" 역할을 사용
            String geminiRole = "assistant".equals(interaction.getRole()) ? "model" : "user";
            // 태그가 포함된 이전 응답은 태그를 제거하여 전달
            String text = "model".equals(geminiRole)
                    ? cleanTags(interaction.getContent())
                    : interaction.getContent();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", geminiRole);
            entry.put("parts", List.of(Map.of("text", text)));
            contents.add(entry);
        }
        return contents;
    }

    // ── 유틸리티 메서드 ──

    /**
     * 응답 텍스트에서 LLM 태그([COUNTER_QUESTION], [NEXT_KEYWORD])를 제거한다.
     */
    private String cleanTags(String text) {
        return text.replace(TAG_COUNTER_QUESTION, "")
                   .replace(TAG_NEXT_KEYWORD, "")
                   .trim();
    }

    /**
     * SseEmitter로 JSON 형태의 SSE 이벤트를 전송한다.
     */
    private void sendSseEvent(SseEmitter emitter, String type, String content) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("content", content);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.error("SSE 이벤트 전송 실패 [type={}]: {}", type, e.getMessage());
        }
    }

    private int getRedisInt(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return (value != null) ? Integer.parseInt(value) : 0;
    }

    private String redisKey(String sessionId, String suffix) {
        return REDIS_PREFIX + sessionId + ":" + suffix;
    }
}
