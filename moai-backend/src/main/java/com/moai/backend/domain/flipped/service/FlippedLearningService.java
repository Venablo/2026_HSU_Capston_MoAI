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
        String firstMessage = generateFirstMessage(firstKeyword);

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
    private String generateFirstMessage(String keyword) {
        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 거꾸로 학습(Flipped Learning) 튜터 AI입니다.

                역할: 학생이 첫 번째 키워드를 스스로 설명하도록 유도하는 세션 시작 안내를 작성합니다.

                ■ 출력 형식: 줄 바꿈을 활용한 텍스트 (마크다운/코드블록/JSON 금지)
                예시 구조:
                  환영 + 세션 목표 한 줄.

                  첫 번째 키워드: [키워드명]
                  아는 만큼 자유롭게 설명해 주세요!

                ■ 규칙
                - 따뜻하고 친근한 한국어 존댓말.
                - 키워드를 별도 줄에 강조해서 보여줄 것.
                - 전문용어는 "한글(영문)" 형태로 1회만 병기.
                - 짧고 부담 없게 — 장황한 가이드 없이 핵심만.
                - 정답 설명 금지. 제어 태그([COUNTER_QUESTION] 등) 금지.
                """;

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
        LlmFlippedEvaluationResult evaluation = evaluateSession(history, curriculum);

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
                                                        WeeklyCurriculum curriculum) {
        String conversationText = history.stream()
                .map(h -> String.format("[%s]: %s", h.getRole(), h.getContent()))
                .collect(Collectors.joining("\n"));

        String keywordList = (curriculum.getKeywords() != null)
                ? String.join(", ", curriculum.getKeywords())
                : curriculum.getTopic();

        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 거꾸로 학습 메타인지 평가 전문가 AI입니다.

                학생과 AI 튜터의 전체 대화 기록을 종합해 학생의 이해도를 평가합니다.

                ■ 분석 항목 (내부 분석용 — JSON 출력에 포함하지 않음):
                1. correct_points: 학생이 정확하게 설명한 포인트 (정의, 원리, 예시, 실전 적용)
                2. incorrect_points: 주제와 무관하거나 완전히 틀린 내용
                3. missing_points: 핵심 개념 중 학생이 언급하지 않은 부분

                ■ 점수 산정 원칙 (매우 중요):
                - 점수는 0에서 시작하여 correct_points에 한해서만 가산한다.
                - 틀린 내용(incorrect_points)과 누락된 내용(missing_points)은 점수에 영향을 주지 않는다 (감점도 없고, 가점도 없다).
                - 참여 자체, 노력, 시도에 대한 기본 점수는 절대 부여하지 않는다.
                - 빈 답변이거나 '모르겠다' 수준 → 반드시 0점.
                - 완전히 틀렸거나 주제와 관련 없는 답변 → 반드시 0점.
                - 핵심 키워드 중 일부를 정확히 설명한 경우에만 해당 비율만큼 점수를 준다.
                  예) 키워드 5개 중 2개를 깊이 있게 설명 → 40점 내외 (2/5 비율).
                - 부분 점수를 세밀하게 반영하여 1점 단위로 정확하게 산정한다.

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                {
                  "score": 0~100 (정수),
                  "flippedResult": "pass", "partial", 또는 "fail" (60점 이상 pass / 30~59점 partial / 30점 미만 fail),
                  "gainedKeywords": ["학생이 정확히 이해한 키워드"],
                  "weakKeywords": ["학생이 틀렸거나 누락한 키워드"],
                  "feedback": "종합 피드백 (한국어). correct_points 요약 + 틀린 내용 교정 + missing_points 보강 제안을 2~4문장으로 통합."
                }

                ■ 필수 규칙:
                1. 틀린 내용은 반드시 피드백에서 올바르게 바로잡을 것.
                2. feedback에 격려 문구를 넣어도 되지만, 격려가 score에 영향을 줘서는 안 된다.
                3. gainedKeywords와 weakKeywords에는 반드시 아래 목록에 있는 키워드만 사용. 임의 생성/변경 금지, 목록 원문 그대로 반환.
                4. 대상 키워드 목록: [%s]
                """, keywordList);

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
                sendSseEvent(emitter, "session_complete", "모든 키워드를 다뤘습니다.");
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
                String systemPrompt
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

            String systemPrompt = buildStreamSystemPrompt(keywords, keywordIndex, (int) exchangeCount);

            return new StreamContext(saved.user(), saved.room(), saved.curriculum(),
                    sessionId, contents, keywords, keywordIndex, exchangeCount, systemPrompt);
        });

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
                        // 버퍼에 남은 토큰 플러시
                        String remaining = tokenBuffer.get().toString();
                        if (!remaining.isBlank()) {
                            String type = counterQuestionMode.get() ? "counter_question" : "token";
                            sendSseEvent(emitter, type, remaining);
                        }

                        String cleanResponse = cleanTags(fullResponse.toString());
                        boolean isCounterQuestion = counterQuestionMode.get();

                        // ── 트랜잭션 3: AI 응답 저장 ──
                        AiInteraction savedAssistant = tx.execute(status -> {
                            AiInteraction assistantInteraction = AiInteraction.builder()
                                    .sessionId(ctx.sessionId())
                                    .user(ctx.user())
                                    .room(ctx.room())
                                    .curriculum(ctx.curriculum())
                                    .role("assistant")
                                    .content(cleanResponse)
                                    .isCounterQuestion(isCounterQuestion)
                                    .build();
                            return aiInteractionRepository.save(assistantInteraction);
                        });

                        // 키워드 전환/완료는 LLM 태그가 아니라 서버의 교환 횟수 기준으로 확정한다.
                        // 각 키워드는 학생 답변 1회 이후 다음 키워드로 넘어가며, 마지막 키워드는 즉시 완료 처리한다.
                        if (ctx.exchangeCount() >= MIN_EXCHANGES_PER_KEYWORD) {
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
     * 토큰 버퍼를 분석하여 태그를 감지하고 적절한 SSE 이벤트를 전송한다.
     *
     * 태그 처리 규칙:
     * - [COUNTER_QUESTION]: 즉시 모드 전환, 이후 토큰은 counter_question 타입으로 전송
     * - [NEXT_KEYWORD]: 교환 횟수 < 2이면 무시, 아니면 키워드 전환 플래그 설정
     */
    private void processTokenBuffer(String buffered, AtomicReference<StringBuilder> tokenBuffer,
                                     AtomicBoolean counterQuestionMode,
                                     AtomicBoolean nextKeywordDetected,
                                     int exchangeCount, SseEmitter emitter) {
        // 태그가 완성되지 않았을 수 있으므로 '[' 이전까지만 전송
        int tagStart = buffered.lastIndexOf('[');

        // '[' 없으면 전체 전송
        if (tagStart < 0) {
            if (!buffered.isEmpty()) {
                String type = counterQuestionMode.get() ? "counter_question" : "token";
                sendSseEvent(emitter, type, buffered);
                tokenBuffer.set(new StringBuilder());
            }
            return;
        }

        // '[' 이전 텍스트가 있으면 전송
        if (tagStart > 0) {
            String beforeTag = buffered.substring(0, tagStart);
            String type = counterQuestionMode.get() ? "counter_question" : "token";
            sendSseEvent(emitter, type, beforeTag);
            tokenBuffer.set(new StringBuilder(buffered.substring(tagStart)));
            buffered = tokenBuffer.get().toString();
        }

        // 완성된 태그 처리
        if (buffered.contains(TAG_COUNTER_QUESTION)) {
            counterQuestionMode.set(true);
            // 태그 이후 텍스트 추출
            String afterTag = buffered.substring(
                    buffered.indexOf(TAG_COUNTER_QUESTION) + TAG_COUNTER_QUESTION.length());
            if (!afterTag.isBlank()) {
                sendSseEvent(emitter, "counter_question", afterTag);
            }
            tokenBuffer.set(new StringBuilder());
        } else if (buffered.contains(TAG_NEXT_KEYWORD)) {
            // 같은 응답에서 역질문이 감지된 경우 키워드 전환 무시 — 학생이 역질문에 답한 뒤 다음 교환에서 처리
            if (counterQuestionMode.get()) {
                log.info("COUNTER_QUESTION과 NEXT_KEYWORD 동시 감지 — NEXT_KEYWORD 무시");
            } else if (exchangeCount >= MIN_EXCHANGES_PER_KEYWORD) {
                nextKeywordDetected.set(true);
            }
            // 태그 이후 텍스트 추출하여 전송
            String afterTag = buffered.substring(
                    buffered.indexOf(TAG_NEXT_KEYWORD) + TAG_NEXT_KEYWORD.length());
            if (!afterTag.isBlank()) {
                String type = counterQuestionMode.get() ? "counter_question" : "token";
                sendSseEvent(emitter, type, afterTag);
            }
            tokenBuffer.set(new StringBuilder());
        }
        // ']'가 아직 없으면 버퍼에 유지 (태그가 아직 완성 중)
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

        if (nextIndex >= keywords.size()) {
            // 모든 키워드 완료 — Redis에 완료 플래그 기록하여 이후 메시지 차단
            redisTemplate.opsForValue().set(redisKey(sessionId, "completed"), "true", SESSION_TTL);
            sendSseEvent(emitter, "session_complete", "모든 키워드를 다뤘습니다!");
        } else {
            // 다음 키워드로 전환: Redis 인덱스 증가, 교환 횟수 리셋
            redisTemplate.opsForValue().set(keywordIndexKey, String.valueOf(nextIndex), SESSION_TTL);
            redisTemplate.opsForValue().set(exchangeCountKey, "0", SESSION_TTL);

            // next_keyword 이벤트: JSON 형태로 키워드 정보 전송
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "next_keyword");
                payload.put("keyword", keywords.get(nextIndex));
                payload.put("keywordIndex", nextIndex);
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
            } catch (IOException e) {
                log.error("next_keyword SSE 전송 실패: {}", e.getMessage());
            }
        }
    }

    /**
     * 시스템 프롬프트를 구성한다.
     * 키워드 목록, 현재 진행 키워드, 교환 횟수 정보를 포함하여
     * LLM이 적절한 시점에 태그를 사용하도록 안내한다.
     */
    private String buildStreamSystemPrompt(List<String> keywords, int keywordIndex, int exchangeCount) {
        String keywordList = String.join(", ", keywords);
        String currentKeyword = keywords.get(keywordIndex);

        return String.format("""
                당신은 MoAI 학습 플랫폼의 거꾸로 학습(Flipped Learning) AI 튜터입니다.
                학생이 키워드를 하나씩 설명하면, 평가나 교정 없이 짧게 호응하고 바로 다음 키워드로 넘어갑니다.

                ## 전체 키워드 목록
                [%s]

                ## 현재 진행 상태
                - 현재 키워드: '%s' (인덱스 %d/%d)
                - 현재 교환 횟수: %d회

                ## 응답 방식
                학생의 설명을 받으면:
                1. 완전히 중립적인 인식 표현 1문장만. 예시: "네, 들었습니다.", "알겠습니다.", "설명해 주셨네요."
                   ※ "잘", "훌륭", "완벽", "정확", "좋은", "깔끔", "멋진" 같은 긍정 평가 어휘 절대 금지.
                   ※ 답변 내용에 대한 어떠한 평가·칭찬·교정도 금지.
                2. 바로 [NEXT_KEYWORD] 태그로 다음 키워드 요청

                다음 키워드를 요청할 때는 줄 바꿈을 활용해 키워드를 눈에 띄게 제시할 것.
                예시:
                  네, 들었습니다.

                  다음 키워드: [키워드명]
                  이 개념도 아는 만큼 자유롭게 설명해 주세요. [NEXT_KEYWORD]

                ## 태그 사용 규칙
                1. 학생 답변을 받으면 항상 [NEXT_KEYWORD]로 다음 키워드로 전환하세요.
                2. [COUNTER_QUESTION] 태그는 사용하지 마세요.
                3. 마지막 키워드에서 [NEXT_KEYWORD]를 쓸 때는 짧은 마무리 인사를 함께 쓰세요.
                4. 태그 자체를 학생에게 보여주지 마세요.

                ## 금지 사항
                - 답변의 옳고 그름·수준 평가 금지 (잘했다/못했다/훌륭하다 등 일체 금지).
                - 올바른 내용으로 교정하거나 보충 설명하는 것 금지.
                - 역질문([COUNTER_QUESTION]) 금지.
                - 장황한 응답 금지 — 중립 인식 1문장 + 다음 키워드 요청만.
                """,
                keywordList, currentKeyword, keywordIndex + 1, keywords.size(),
                exchangeCount, currentKeyword
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
