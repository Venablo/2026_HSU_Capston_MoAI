package com.moai.backend.domain.learningroom.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService.WeekEnrichmentContext;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Value("${moai.files.local-root:../data}")
    private String localFileRoot;

    public List<LearningRoomListResponseDto> getMyRooms(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return learningRoomRepository.findByUserId(user.getId()).stream()
                .map(LearningRoomListResponseDto::from)
                .toList();
    }

    @Transactional
    public LearningRoomCreateResponseDto createRoom(String email, LearningRoomCreateRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. LearningRoom INSERT
        LearningRoom room = LearningRoom.builder()
                .user(user)
                .subject(requestDto.getSubject())
                .level(requestDto.getLevel())
                .durationWeeks(requestDto.getDurationWeeks())
                .hoursPerDay(requestDto.getHoursPerDay())
                .build();
        learningRoomRepository.save(room);

        // 2. P1 프롬프트 — 주차별 학습 주제 + 핵심 키워드 생성 (동기 1회 호출)
        P1CurriculumResponse p1Response = generateCurriculum(
                requestDto.getSubject(),
                requestDto.getLevel(),
                requestDto.getDurationWeeks(),
                requestDto.getHoursPerDay().doubleValue()
        );

        // 3. WeeklyCurriculum INSERT × N주 + 병렬 enrichment 컨텍스트 수집
        List<WeeklyCurriculum> savedCurriculums = new ArrayList<>();
        List<WeekEnrichmentContext> enrichmentContexts = new ArrayList<>();
        List<LearningRoomCreateResponseDto.CurriculumSummary> summaries = new ArrayList<>();

        for (P1CurriculumResponse.WeekItem week : p1Response.getWeeklyTopics()) {
            WeeklyCurriculum curriculum = WeeklyCurriculum.builder()
                    .room(room)
                    .weekNumber((short) week.getWeekNumber())
                    .topic(week.getTopic())
                    .description(week.getWeeklySummary())
                    .keywords(null)
                    .build();
            weeklyCurriculumRepository.save(curriculum);
            savedCurriculums.add(curriculum);

            enrichmentContexts.add(new WeekEnrichmentContext(
                    curriculum.getId(),
                    week.getTopic(),
                    week.getWeeklySummary(),
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

        // 4. 주차별 P2 enrichment를 비동기 병렬 실행 — 트랜잭션 커밋 후에 실행해야
        // async 스레드의 findById()가 방금 저장한 행을 읽을 수 있다.
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
                      "weekly_summary": "이번 주차에서 배우게 될 범위, 앞주차와의 연결, 왜 중요한지를 2~3문장으로 설명한 요약",
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
                4. weekly_summary는 2~3문장으로 "왜 중요한지 / 앞뒤 주차와 어떻게 연결되는지 / 실전에서 어디에 쓰이는지" 포함
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

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : Collections.emptyList();
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
