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

        return new FlippedStartResponseDto(
                sessionId, firstMessage, keywords, 0, keywords.size()
        );
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
                당신은 거꾸로 학습(Flipped Learning)을 진행하는 AI 튜터입니다.
                학생이 이번 주차에서 배운 내용을 직접 설명하도록 유도하는 것이 목표입니다.
                첫 안내 문구를 친근하고 격려하는 톤으로 작성해주세요.
                하나의 키워드에 대해 설명을 요청하는 형태로 작성합니다.
                응답은 한국어로, 2~3문장 이내로 작성해주세요.
                """;

        String userMessage = String.format(
                "첫 번째 키워드는 '%s'입니다. 학생에게 이 키워드에 대해 설명해달라고 요청하는 안내 문구를 작성해주세요.",
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

        // 5. 매칭 엔진 실행 (PHASE 7에서 구현 예정)
        // TODO: matchingEngineService.tryMatch(user, room, evaluation.getWeakKeywords());

        // 6. 주차 진척도 += 30%, 학습실 전체 진척도 재계산
        updateCompletionRates(curriculum, room);

        // 7. Redis 세션 상태 정리
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

        String systemPrompt = """
                당신은 거꾸로 학습 평가 AI입니다.
                학생과 AI 튜터의 대화 내용을 분석하여 학생의 이해도를 평가합니다.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.

                {
                  "score": 0~100 사이의 숫자 (이해도 점수),
                  "flippedResult": "pass" 또는 "fail" (60점 이상이면 pass),
                  "gainedKeywords": ["학생이 잘 이해한 키워드 목록"],
                  "weakKeywords": ["학생이 부족한 키워드 목록"],
                  "feedback": "학생에게 전달할 종합 피드백 (한국어, 2~3문장)"
                }
                """;

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
    }

    // ── 5-3: SSE 스트리밍 채팅 ──

    // 키워드당 최소/최대 교환 횟수
    private static final int MIN_EXCHANGES_PER_KEYWORD = 2;
    private static final int MAX_EXCHANGES_PER_KEYWORD = 5;

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

                        // 키워드 전환 처리: [NEXT_KEYWORD] 감지 또는 최대 교환 횟수 도달
                        boolean forceTransition = (ctx.exchangeCount() >= MAX_EXCHANGES_PER_KEYWORD)
                                && !nextKeywordDetected.get();

                        if (nextKeywordDetected.get() || forceTransition) {
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
            // 최소 교환 횟수 미달이면 태그 무시
            if (exchangeCount >= MIN_EXCHANGES_PER_KEYWORD) {
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
            // 모든 키워드 완료
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
                당신은 거꾸로 학습(Flipped Learning)의 AI 튜터입니다.
                학생이 키워드를 하나씩 설명하면 경청하고, 이해한 부분을 칭찬합니다.
                부족한 부분이 있으면 역질문을 통해 학생이 스스로 깨닫도록 유도합니다.

                ## 전체 키워드 목록
                [%s]

                ## 현재 진행 상태
                - 현재 키워드: '%s' (인덱스 %d/%d)
                - 현재 교환 횟수: %d회

                ## 태그 사용 규칙
                1. 역질문을 할 때는 반드시 [COUNTER_QUESTION] 태그를 역질문 텍스트 바로 앞에 붙여주세요.
                2. 학생이 현재 키워드를 충분히 이해했다고 판단되면 [NEXT_KEYWORD] 태그를 응답 끝에 붙여주세요.
                   - 단, 현재 교환 횟수가 2회 미만이면 [NEXT_KEYWORD]를 사용하지 마세요.
                3. 태그는 텍스트 내에 자연스럽게 포함시키되, 태그 자체를 학생에게 보여주지는 마세요.

                ## 응답 규칙
                - 응답은 한국어로 작성합니다.
                - 현재 키워드('%s')에 집중하여 대화합니다.
                - 칭찬 → 보충 설명 또는 역질문 순서로 응답합니다.
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
