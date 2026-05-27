package com.moai.backend.domain.learningroom.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService.WeekEnrichmentContext;
import com.moai.backend.domain.demo.entity.MockCurriculumTemplate;
import com.moai.backend.domain.demo.entity.MockTranscriptTemplate;
import com.moai.backend.domain.demo.repository.MockCurriculumTemplateRepository;
import com.moai.backend.domain.demo.repository.MockTranscriptTemplateRepository;
import com.moai.backend.domain.demo.service.DemoResetService;
import com.moai.backend.domain.eventlog.repository.LearningEventLogRepository;
import com.moai.backend.domain.flipped.repository.AiInteractionRepository;
import com.moai.backend.domain.flipped.repository.FlippedSessionRepository;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.dto.LearningRoomCreateRequestDto;
import com.moai.backend.domain.learningroom.dto.LearningRoomCreateResponseDto;
import com.moai.backend.domain.learningroom.dto.LearningRoomListResponseDto;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.material.repository.CustomMaterialRepository;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizReportRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.transcript.entity.VideoTranscript;
import com.moai.backend.domain.transcript.repository.VideoTranscriptRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningRoomService {

    private final LearningRoomRepository learningRoomRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final UserRepository userRepository;
    private final LlmService llmService;
    private final CurriculumEnrichmentService curriculumEnrichmentService;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizReportRepository quizReportRepository;
    private final QuizRepository quizRepository;
    private final VideoTranscriptRepository videoTranscriptRepository;
    private final LearningEventLogRepository learningEventLogRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final FlippedSessionRepository flippedSessionRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final CustomMaterialRepository customMaterialRepository;
    private final MockCurriculumTemplateRepository mockCurriculumTemplateRepository;
    private final MockTranscriptTemplateRepository mockTranscriptTemplateRepository;
    private final DemoResetService demoResetService;
    private final PlatformTransactionManager transactionManager;

    // 시연 모드 분기 키워드 — 일치 시 LLM 호출 없이 mock 템플릿 복사.
    private static final String DEMO_SUBJECT = "정보처리기사";

    @Value("${moai.files.local-root:../data}")
    private String localFileRoot;

    public List<LearningRoomListResponseDto> getMyRooms(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return learningRoomRepository.findByUserId(user.getId()).stream()
                .map(LearningRoomListResponseDto::from)
                .toList();
    }

    // NOT_SUPPORTED: 클래스 레벨 @Transactional(readOnly=true)을 억제하여 LLM 호출 중 DB 커넥션을 점유하지 않는다.
    // LLM 호출(수십 초) → 완료 후 TransactionTemplate으로 짧게 쓰기 트랜잭션만 열기.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LearningRoomCreateResponseDto createRoom(String email, LearningRoomCreateRequestDto requestDto) {
        // 시연 모드 분기: subject == "정보처리기사" AND 화이트리스트(moai.demo.cleanup-login-ids) 등록된
        // 시연 계정일 때만 mock 템플릿 복사로 우회. 그 외(일반 사용자가 "정보처리기사" 입력 / 시연 계정이
        // 다른 subject 입력) 케이스는 모두 정상 LLM 생성 흐름으로 폴백한다.
        if (DEMO_SUBJECT.equals(requestDto.getSubject())) {
            User candidate = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (demoResetService.isDemoAccount(candidate.getLoginId())) {
                return copyFromTemplate(email, requestDto);
            }
            // 화이트리스트 미포함 → LLM 흐름으로 폴백 (아래 정상 경로 진입)
        }

        // 1. LLM 호출 — DB 커넥션 미점유 상태에서 실행
        P1CurriculumResponse p1Response = generateCurriculum(
                requestDto.getSubject(),
                requestDto.getLevel(),
                requestDto.getDurationWeeks(),
                requestDto.getHoursPerDay().doubleValue()
        );

        // 2. LLM 결과 획득 후 DB 저장만 짧게 트랜잭션으로 감싸기
        LearningRoomCreateResponseDto result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            LearningRoom room = LearningRoom.builder()
                    .user(user)
                    .subject(requestDto.getSubject())
                    .level(requestDto.getLevel())
                    .durationWeeks(requestDto.getDurationWeeks())
                    .hoursPerDay(requestDto.getHoursPerDay())
                    .build();
            learningRoomRepository.save(room);

            List<WeekEnrichmentContext> enrichmentContexts = new ArrayList<>();
            List<LearningRoomCreateResponseDto.CurriculumSummary> summaries = new ArrayList<>();

            for (P1CurriculumResponse.WeekItem week : p1Response.getWeeklyTopics()) {
                String normalizedSummary = normalizeBullets(week.getWeeklySummary());
                WeeklyCurriculum curriculum = WeeklyCurriculum.builder()
                        .room(room)
                        .weekNumber((short) week.getWeekNumber())
                        .topic(week.getTopic())
                        .description(normalizedSummary)
                        .keywords(initialKeywords(week))
                        .build();
                weeklyCurriculumRepository.save(curriculum);

                enrichmentContexts.add(new WeekEnrichmentContext(
                        curriculum.getId(),
                        week.getTopic(),
                        normalizedSummary,
                        nullSafe(week.getLearningObjectives()),
                        nullSafe(week.getKeyConcepts()),
                        nullSafe(week.getFocusQuestions()),
                        nullSafe(week.getPracticeKeywords()),
                        week.getYoutubeSearchQuery(),
                        requestDto.getSubject(),
                        requestDto.getLevel()
                ));

                summaries.add(new LearningRoomCreateResponseDto.CurriculumSummary(
                        week.getWeekNumber(), week.getTopic()
                ));
            }

            // P2 enrichment는 트랜잭션 커밋 후 비동기 실행 — async 스레드가 저장된 행을 읽을 수 있도록
            final List<WeekEnrichmentContext> contextsToEnrich = enrichmentContexts;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (WeekEnrichmentContext ctx : contextsToEnrich) {
                        curriculumEnrichmentService.enrichWeek(ctx);
                    }
                }
            });

            return LearningRoomCreateResponseDto.builder()
                    .roomId(room.getId())
                    .subject(room.getSubject())
                    .level(room.getLevel())
                    .durationWeeks(room.getDurationWeeks())
                    .curriculum(summaries)
                    .build();
        });

        if (result == null) throw new CustomException(ErrorCode.LLM_API_CALL_FAILED);
        return result;
    }

    @Transactional
    public void deleteRoom(String email, String roomId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        List<String> curriculumIds = weeklyCurriculumRepository.findIdsByRoomIdOrderByWeekNumber(roomId);

        if (!curriculumIds.isEmpty()) {
            // 1. 퀴즈 응시 → 퀴즈 문항 → 퀴즈 리포트 → 퀴즈 (FK 순서대로 삭제)
            quizAttemptRepository.deleteByCurriculumIdIn(curriculumIds);
            quizReportRepository.deleteByCurriculumIdIn(curriculumIds);
            quizQuestionRepository.deleteByCurriculumIdIn(curriculumIds);
            quizRepository.deleteByCurriculumIdIn(curriculumIds);

            // 2. 자막, 이벤트 로그
            videoTranscriptRepository.deleteByCurriculumIdIn(curriculumIds);
            learningEventLogRepository.deleteByCurriculumIdIn(curriculumIds);
        }

        // 3. 거꾸로 학습 세션 및 AI 인터랙션 (roomId 기준)
        aiInteractionRepository.deleteByRoomId(roomId);
        flippedSessionRepository.deleteByRoomId(roomId);

        // 4. 사용자 키워드 (roomId 기준)
        userKeywordRepository.deleteByRoomId(roomId);

        customMaterialRepository.deleteByRoomId(roomId);

        // 5. 주차 커리큘럼 → 학습실
        weeklyCurriculumRepository.deleteByRoomId(roomId);
        learningRoomRepository.delete(room);
        deleteLocalMaterialFilesAfterCommit(roomId);

        log.info("학습실 삭제 완료: roomId={}, email={}", roomId, email);
    }

    private void deleteLocalMaterialFilesAfterCommit(String roomId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteLocalMaterialFiles(roomId);
                }
            });
            return;
        }

        deleteLocalMaterialFiles(roomId);
    }

    private void deleteLocalMaterialFiles(String roomId) {
        Path baseDir = Paths.get(localFileRoot).toAbsolutePath().normalize();
        Path materialDir = baseDir.resolve("materials").resolve(roomId).normalize();

        if (!materialDir.startsWith(baseDir) || !Files.exists(materialDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(materialDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete local material file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete local material directory: {}", materialDir, e);
        }
    }

    /**
     * 시연용: 정보처리기사 학습실 생성 시 mock_curriculum_templates / mock_transcript_templates
     * 에서 weekly_curriculums / video_transcripts 로 복사한다. LLM 호출 없음.
     * mock 엔티티가 WeeklyCurriculum 과 동일한 컨버터(List<String>, List<CurriculumResource>)를 사용하므로
     * 변환 없이 필드 매핑만으로 복사 가능.
     */
    private LearningRoomCreateResponseDto copyFromTemplate(String email, LearningRoomCreateRequestDto requestDto) {
        // 트랜잭션 안에서 생성된 주차 id 를 모아둔다. 커밋 후 비동기 Step D 호출에 사용.
        List<String> copiedCurriculumIds = new ArrayList<>();
        String[] subjectLevel = new String[2];  // [0]=subject, [1]=level

        LearningRoomCreateResponseDto result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            LearningRoom room = LearningRoom.builder()
                    .user(user)
                    .subject(requestDto.getSubject())
                    .level(requestDto.getLevel())
                    .durationWeeks(requestDto.getDurationWeeks())
                    .hoursPerDay(requestDto.getHoursPerDay())
                    .build();
            learningRoomRepository.save(room);

            subjectLevel[0] = room.getSubject();
            subjectLevel[1] = room.getLevel();

            List<MockCurriculumTemplate> templates =
                    mockCurriculumTemplateRepository.findAllByOrderByWeekNumberAsc();

            List<LearningRoomCreateResponseDto.CurriculumSummary> summaries = new ArrayList<>();

            for (MockCurriculumTemplate tmpl : templates) {
                WeeklyCurriculum curriculum = WeeklyCurriculum.builder()
                        .room(room)
                        .weekNumber(tmpl.getWeekNumber())
                        .topic(tmpl.getTopic())
                        .description(tmpl.getDescription())
                        .keywords(tmpl.getKeywords())
                        .resources(tmpl.getResources())
                        .build();
                weeklyCurriculumRepository.save(curriculum);
                copiedCurriculumIds.add(curriculum.getId());

                List<MockTranscriptTemplate> transcripts =
                        mockTranscriptTemplateRepository.findByTemplateIdOrderByChunkIndexAsc(tmpl.getId());
                for (MockTranscriptTemplate t : transcripts) {
                    videoTranscriptRepository.save(VideoTranscript.builder()
                            .curriculum(curriculum)
                            .videoId(t.getVideoId())
                            .startSec(t.getStartSec())
                            .endSec(t.getEndSec())
                            .textContent(t.getTextContent())
                            .chunkIndex(t.getChunkIndex())
                            .build());
                }

                summaries.add(new LearningRoomCreateResponseDto.CurriculumSummary(
                        tmpl.getWeekNumber().intValue(), tmpl.getTopic()
                ));
            }

            log.info("[demo] 정보처리기사 학습실 생성 완료 — LLM 호출 0회, roomId={}, weeks={}",
                    room.getId(), templates.size());

            return LearningRoomCreateResponseDto.builder()
                    .roomId(room.getId())
                    .subject(room.getSubject())
                    .level(room.getLevel())
                    .durationWeeks(room.getDurationWeeks())
                    .curriculum(summaries)
                    .build();
        });

        if (result == null) throw new CustomException(ErrorCode.LLM_API_CALL_FAILED);

        // 트랜잭션 커밋 후 — 주차별 Markdown 학습 자료를 백그라운드 생성.
        // 학습실 생성 응답은 이미 빌드되어 사용자에게 즉시 반환되고,
        // 자료는 잠시 후 weekly_curriculums.resources 에 Markdown 항목으로 추가된다.
        // 각 호출은 @Async("curriculumTaskExecutor") 로 독립 실행되어 한 주차 실패가 격리된다.
        for (String curriculumId : copiedCurriculumIds) {
            curriculumEnrichmentService.generateDemoMaterialForWeek(
                    curriculumId, subjectLevel[0], subjectLevel[1]
            );
        }

        return result;
    }

    private P1CurriculumResponse generateCurriculum(String subject, String level,
                                                     short durationWeeks, double hoursPerDay) {
        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 커리큘럼 설계 전문가 AI입니다.

                학습 정보를 받아 주차별 학습 주제 목록을 생성하세요.

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                {
                  "weekly_topics": [
                    {
                      "week_number": 1,
                      "topic": "구체적 학습 주제명 — 세부범위",
                      "weekly_summary": "이번 주차 흐름을 자연스러운 1~2문장으로 소개한 뒤, 핵심 포인트를 불릿 2~3개로 정리한 텍스트",
                      "learning_objectives": ["목표1", "목표2", "목표3", "목표4"],
                      "key_concepts": ["핵심 키워드1", "키워드2", "키워드3", "키워드4", "키워드5"],
                      "focus_questions": ["스스로 답해야 할 핵심 질문1", "질문2"],
                      "practice_keywords": ["반복 복습 키워드1", "키워드2", "키워드3", "키워드4"],
                      "youtube_search_query": "이 주차 내용을 가장 잘 설명하는 한국어 YouTube 검색어"
                    }
                  ],
                  "recommended_channel_query": "이 과목 전체를 다루는 대표 YouTube 채널 검색어"
                }

                ■ 필수 규칙:
                1. 요청된 주차 수 전부 빠짐없이 생성
                2. topic은 반드시 대주제 — 세부범위 형태로 구체적 작성
                3. 주차 간 선수학습→심화 논리적 연결 (1주차 기초 → 마지막 주차 종합)
                4. weekly_summary는 도입 문장(1~2문장) + 핵심 포인트 불릿(2~3개) 혼합 구성. 전체가 한 덩어리 글이 되지 않도록 불릿으로 시각적으로 분리할 것. 지나치게 길지 않게, 핵심만 간결하게. 불릿 항목 사이에는 빈 줄(연속 개행) 절대 금지 — 각 불릿은 \\n 하나로만 구분. 마크다운 헤더(#, ##, ###) 절대 금지. 불릿 앞에 **볼드 레이블**: 형태 삽입 금지 — 불릿 텍스트는 그냥 평문으로 작성.
                5. key_concepts는 최소 5개 이상 (시험 출제 키워드 중심)
                6. learning_objectives는 최소 4개 이상, 행동동사 사용 (설명할 수 있다, 비교할 수 있다, 구현할 수 있다)
                7. focus_questions는 최소 2개 이상
                8. practice_keywords는 최소 4개 이상
                9. 난이도 반영: beginner=기초개념/용어정리, intermediate=실무적용/문제풀이, advanced=심화분석/최적화
                10. youtube_search_query는 실제 YouTube에서 검색 가능한 구체적 한국어 키워드 조합
                """;

        String userMessage = String.format(
                "학습 주제: %s\n학습 수준: %s\n총 기간: %d주\n하루 학습 시간: %.1f시간",
                subject, level, durationWeeks, hoursPerDay
        );

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        return llmService.callJson(request, P1CurriculumResponse.class);
    }

    /** 불릿(-) 항목 앞의 연속 개행(\n\n-)을 단일 개행(\n-)으로 압축한다. LLM이 프롬프트 무시 시 후처리로 보장. */
    private static String normalizeBullets(String text) {
        if (text == null) return null;
        return text.replaceAll("\\n{2,}(?=-)", "\n");
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private List<String> initialKeywords(P1CurriculumResponse.WeekItem week) {
        List<String> keyConcepts = cleanKeywordList(week.getKeyConcepts());
        if (!keyConcepts.isEmpty()) return keyConcepts;

        List<String> practiceKeywords = cleanKeywordList(week.getPracticeKeywords());
        if (!practiceKeywords.isEmpty()) return practiceKeywords;

        String topic = week.getTopic();
        if (topic != null && !topic.isBlank()) return List.of(topic.trim());
        return Collections.emptyList();
    }

    private List<String> cleanKeywordList(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();
        return keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Getter
    @NoArgsConstructor
    static class P1CurriculumResponse {
        @JsonProperty("weekly_topics")
        private List<WeekItem> weeklyTopics;

        @JsonProperty("recommended_channel_query")
        private String recommendedChannelQuery;

        @Getter
        @NoArgsConstructor
        static class WeekItem {
            @JsonProperty("week_number")
            private int weekNumber;
            private String topic;

            @JsonProperty("weekly_summary")
            private String weeklySummary;

            @JsonProperty("learning_objectives")
            private List<String> learningObjectives;

            @JsonProperty("key_concepts")
            private List<String> keyConcepts;

            @JsonProperty("focus_questions")
            private List<String> focusQuestions;

            @JsonProperty("practice_keywords")
            private List<String> practiceKeywords;

            @JsonProperty("youtube_search_query")
            private String youtubeSearchQuery;
        }
    }
}
