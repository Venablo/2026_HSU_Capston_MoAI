package com.moai.backend.domain.eventlog.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.eventlog.dto.LlmKeywordExtractionResult;
import com.moai.backend.domain.eventlog.dto.LlmQuizResult;
import com.moai.backend.domain.eventlog.dto.LlmSummaryResult;
import com.moai.backend.domain.eventlog.entity.LearningEventLog;
import com.moai.backend.domain.eventlog.repository.LearningEventLogRepository;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.material.entity.CustomMaterial;
import com.moai.backend.domain.material.entity.SummaryItem;
import com.moai.backend.domain.material.repository.CustomMaterialRepository;
import com.moai.backend.domain.quiz.entity.Quiz;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.transcript.entity.VideoTranscript;
import com.moai.backend.domain.transcript.repository.VideoTranscriptRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventProcessingService {

    private final LearningEventLogRepository eventLogRepository;
    private final VideoTranscriptRepository transcriptRepository;
    private final CustomMaterialRepository materialRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final LlmService llmService;

    private static final int REWIND_LOOKAHEAD_SEC = 30;
    private static final int PAUSE_LOOKBACK_SEC = 30;

    // 한국어 문장 종결어미 패턴 — 구절은 허용, 문장은 거부
    private static final java.util.regex.Pattern SENTENCE_ENDING =
            java.util.regex.Pattern.compile(".+(니다|ㅂ니다|어요|아요|이요|한다|된다|였다|았다|었다|겠다|합니다|됩니다)$");

    // ──────────────────────────────────────────────
    // 패턴1: 되감기 발동 처리
    // ──────────────────────────────────────────────

    /**
     * 되감기 패턴 발동 시 처리 흐름:
     * 1) 이벤트 로그 저장
     * 2) 되감기 지점부터 30초 앞 구간 자막 조회 → 텍스트 합치기
     * 3) LLM 키워드 추출 → 커리큘럼 키워드와 교집합 필터링
     * 4) LLM 요약 자료 생성
     * 5) CustomMaterial 저장
     * 6) UserKeyword 약점 upsert
     */
    @Transactional
    public MaterialProcessResult processRewindPattern(User user, LearningRoom room,
                                                     WeeklyCurriculum curriculum,
                                                     String videoId, double rewindTargetSec,
                                                     String payloadJson) {
        BigDecimal fromSec = BigDecimal.valueOf(rewindTargetSec);
        BigDecimal toSec = BigDecimal.valueOf(rewindTargetSec + REWIND_LOOKAHEAD_SEC);
        return buildMaterialFromSegment(user, room, curriculum, videoId, "video_rewind",
                fromSec, toSec, rewindTargetSec, payloadJson);
    }

    // ──────────────────────────────────────────────
    // 패턴2: 일시정지 / 탭 이탈 처리
    // ──────────────────────────────────────────────

    /**
     * 일시정지·탭 이탈 시 처리 흐름은 되감기와 동일하나,
     * 자막 조회 구간이 이벤트 발생 시점 기준 **이전 30초**라는 점이 다르다.
     * eventType은 "video_pause" 또는 "tab_departure" 중 하나가 들어온다.
     */
    @Transactional
    public MaterialProcessResult processPauseOrDeparturePattern(User user, LearningRoom room,
                                                                 WeeklyCurriculum curriculum,
                                                                 String videoId, String eventType,
                                                                 double eventTimeSec,
                                                                 String payloadJson) {
        double from = Math.max(0.0, eventTimeSec - PAUSE_LOOKBACK_SEC);
        BigDecimal fromSec = BigDecimal.valueOf(from);
        BigDecimal toSec = BigDecimal.valueOf(eventTimeSec);
        return buildMaterialFromSegment(user, room, curriculum, videoId, eventType,
                fromSec, toSec, eventTimeSec, payloadJson);
    }

    // ──────────────────────────────────────────────
    // 공통 자료 생성 시퀀스
    // ──────────────────────────────────────────────

    private MaterialProcessResult buildMaterialFromSegment(User user, LearningRoom room,
                                                            WeeklyCurriculum curriculum,
                                                            String videoId, String eventType,
                                                            BigDecimal fromSec, BigDecimal toSec,
                                                            double segmentLabelSec,
                                                            String payloadJson) {
        // 1. 이벤트 로그 저장
        saveEventLog(user, curriculum, videoId, eventType, payloadJson);

        // 2. 구간과 겹치는 자막 조회 (start_sec <= toSec AND end_sec >= fromSec)
        List<VideoTranscript> transcripts = transcriptRepository
                .findByCurriculumIdAndVideoIdAndStartSecLessThanEqualAndEndSecGreaterThanEqualOrderByChunkIndex(
                        curriculum.getId(), videoId, toSec, fromSec);

        String transcriptText = joinTranscriptTexts(transcripts);
        if (transcriptText.isBlank()) {
            switch (eventType) {
                case "video_rewind" -> log.warn("되감기 구간 자막 없음 — curriculum={}, video={}, from={}, to={}",
                        curriculum.getId(), videoId, fromSec, toSec);
                case "video_pause" -> log.warn("일시정지 구간 자막 없음 — curriculum={}, video={}, from={}, to={}",
                        curriculum.getId(), videoId, fromSec, toSec);
                case "tab_departure" -> log.warn("탭 이탈 구간 자막 없음 — curriculum={}, video={}, from={}, to={}",
                        curriculum.getId(), videoId, fromSec, toSec);
                default -> log.warn("이벤트 구간 자막 없음 — eventType={}, curriculum={}, video={}, from={}, to={}",
                        eventType, curriculum.getId(), videoId, fromSec, toSec);
            }
            return new MaterialProcessResult(Collections.emptyList(), null);
        }

        // 3. LLM 키워드 추출 — 탭 이탈은 단어 1개 전용 경로, 나머지는 커리큘럼 교집합 필터링
        List<String> filteredKeywords = "tab_departure".equals(eventType)
                ? extractSingleKeywordForDeparture(transcriptText, room.getSubject(), curriculum.getTopic())
                : extractAndFilterKeywords(transcriptText, curriculum);

        if (filteredKeywords.isEmpty()) {
            switch (eventType) {
                case "video_rewind" -> log.info("되감기 구간에서 커리큘럼 키워드와 일치하는 키워드 없음");
                case "video_pause" -> log.info("일시정지 구간에서 커리큘럼 키워드와 일치하는 키워드 없음");
                case "tab_departure" -> log.info("탭 이탈 구간에서 대주제 관련 키워드 없음");
                default -> log.info("이벤트 구간에서 커리큘럼 키워드와 일치하는 키워드 없음 — eventType={}", eventType);
            }
            return new MaterialProcessResult(Collections.emptyList(), null);
        }

        // 4. LLM 요약 자료 생성 (필터링된 키워드 기반)
        LlmSummaryResult summary = generateSummary(filteredKeywords, transcriptText, room);

        // 5. CustomMaterial 저장
        String videoSegment = formatVideoSegment(segmentLabelSec);
        CustomMaterial material = CustomMaterial.builder()
                .user(user)
                .room(room)
                .curriculum(curriculum)
                .triggerKeywords(filteredKeywords)
                .videoSegment(videoSegment)
                .title(summary.getTitle())
                .summaryItems(summary.getSummaryItems())
                .build();
        materialRepository.save(material);

        // 6. 필터링된 키워드마다 UserKeyword 약점 upsert
        upsertWeaknessKeywords(user, room, curriculum, filteredKeywords);

        return new MaterialProcessResult(filteredKeywords, material.getId());
    }

    // ──────────────────────────────────────────────
    // 패턴3/4: 스킵·2배속 발동 처리
    // ──────────────────────────────────────────────

    /**
     * 스킵/2배속 패턴 발동 시 처리 흐름:
     * 1) 이벤트 로그 저장
     * 2) 해당 구간 자막 조회
     * 3) LLM 키워드 추출 → 커리큘럼 키워드와 교집합 필터링
     * 4) LLM 4지선다 퀴즈 1문제 생성 (필터링된 키워드 + 자막 기반)
     * 5) Quiz + QuizQuestion 저장
     */
    @Transactional
    public QuizProcessResult processSkipOrSpeedUpPattern(User user, LearningRoom room,
                                                          WeeklyCurriculum curriculum,
                                                          String videoId, String eventType,
                                                          double fromSec, double toSec,
                                                          int rewindToSec, String payloadJson) {
        // 1. 이벤트 로그 저장
        saveEventLog(user, curriculum, videoId, eventType, payloadJson);

        // 2. 구간 범위와 겹치는 자막 조회 (start_sec <= toSec AND end_sec >= fromSec)
        BigDecimal from = BigDecimal.valueOf(fromSec);
        BigDecimal to = BigDecimal.valueOf(toSec);
        List<VideoTranscript> transcripts = transcriptRepository
                .findByCurriculumIdAndVideoIdAndStartSecLessThanEqualAndEndSecGreaterThanEqualOrderByChunkIndex(
                        curriculum.getId(), videoId, to, from);

        String transcriptText = joinTranscriptTexts(transcripts);
        if (transcriptText.isBlank()) {
            log.warn("스킵/2배속 구간 자막 없음 — curriculum={}, video={}, from={}, to={}",
                    curriculum.getId(), videoId, fromSec, toSec);
        }

        // 3. LLM 키워드 추출 → 커리큘럼 키워드와 교집합 필터링
        //    자막이 없는 경우 커리큘럼 키워드 전체를 fallback으로 사용
        List<String> filteredKeywords = transcriptText.isBlank()
                ? (curriculum.getKeywords() != null ? curriculum.getKeywords() : Collections.emptyList())
                : extractAndFilterKeywords(transcriptText, curriculum);

        // 4. LLM 4지선다 퀴즈 1문제 생성 — 여러 키워드 중 첫 번째 하나에 대해서만 출제
        String targetKeyword = filteredKeywords.isEmpty() ? curriculum.getTopic() : filteredKeywords.get(0);
        LlmQuizResult quizResult = generateQuiz(targetKeyword, transcriptText, curriculum.getTopic(), room);

        // 5. Quiz + QuizQuestion 저장
        Quiz quiz = Quiz.builder()
                .curriculum(curriculum)
                .quizType("multiple_popup")
                .title(eventType.equals("video_skip") ? "스킵 구간 돌발 퀴즈" : "2배속 구간 돌발 퀴즈")
                .rewindToSec(rewindToSec)
                .build();
        quizRepository.save(quiz);

        QuizQuestion question = QuizQuestion.builder()
                .quiz(quiz)
                .questionType("multiple")
                .question(quizResult.getQuestion())
                .options(quizResult.getOptions())
                .answer(quizResult.getAnswer())
                .questionOrder((short) 1)
                .relatedKeyword(quizResult.getRelatedKeyword())
                .timeLimitSec((short) 60)
                .build();
        quizQuestionRepository.save(question);

        return new QuizProcessResult(quiz.getId(), question.getId());
    }

    // ──────────────────────────────────────────────
    // 공통 내부 메서드
    // ──────────────────────────────────────────────

    private void saveEventLog(User user, WeeklyCurriculum curriculum,
                              String videoId, String eventType, String payloadJson) {
        LearningEventLog eventLog = LearningEventLog.builder()
                .user(user)
                .curriculum(curriculum)
                .videoId(videoId)
                .eventType(eventType)
                .payload(payloadJson)
                .aiTriggered(true)
                .loggedAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(eventLog);
    }

    /**
     * 자막 청크 리스트의 텍스트를 하나로 합친다.
     */
    private String joinTranscriptTexts(List<VideoTranscript> transcripts) {
        if (transcripts == null || transcripts.isEmpty()) {
            return "";
        }
        return transcripts.stream()
                .map(VideoTranscript::getTextContent)
                .collect(Collectors.joining(" "));
    }

    /**
     * 탭 이탈 전용: 자막에서 방의 대주제와 관련된 핵심 단어 하나를 AI에게 추출 요청한다.
     * 커리큘럼 키워드 목록과의 exact match 없이 대주제 관련성만 판단한다.
     */
    private List<String> extractSingleKeywordForDeparture(String transcriptText, String subject, String topic) {
        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 강의 자막 분석 AI입니다.

                역할: 학습자가 탭을 이탈한 구간의 자막에서 핵심 키워드 단어 하나를 추출합니다.

                ■ 학습 대주제: %s
                ■ 이번 주차 주제: %s

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 금지)
                {"keyword": "단어"}

                ■ 필수 규칙
                1. 반드시 명사 또는 전문 용어 단 하나만 추출. 구절·문장·조사 금지.
                2. 학습 대주제(%s)와 관련된 용어면 모두 유효. 별도 목록에 없어도 관련 개념이면 허용.
                3. 자막 전체에서 핵심도가 가장 높은 단어 하나만 선택.
                4. 자막이 너무 짧거나 대주제와 무관하면 빈 문자열: {"keyword": ""}
                """.formatted(subject, topic, subject);

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage("자막 원문:\n" + transcriptText)
                .build();

        LlmSingleKeywordResult result = llmService.callJson(request, LlmSingleKeywordResult.class);
        String keyword = result != null ? result.getKeyword() : null;

        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = keyword.trim();
        if (SENTENCE_ENDING.matcher(trimmed).matches()) {
            log.info("탭 이탈 키워드가 문장으로 추출됨 — 무시: {}", trimmed);
            return Collections.emptyList();
        }
        return Collections.singletonList(trimmed);
    }

    /**
     * LLM으로 자막에서 키워드를 추출한 뒤,
     * 커리큘럼에 등록된 키워드와 교집합하여 관련 키워드만 필터링한다.
     */
    private List<String> extractAndFilterKeywords(String transcriptText, WeeklyCurriculum curriculum) {
        List<String> curriculumKw = curriculum.getKeywords();
        String allowList = (curriculumKw == null || curriculumKw.isEmpty())
                ? "(비어있음)" : String.join(", ", curriculumKw);

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 강의 자막 분석 AI입니다.

                역할: 주어진 자막 구간에서 학생이 해당 시점에 **다루고 있는 학습 키워드**를 추출해 패턴3(개념 혼동) 감지에 사용합니다.

                ■ 출력 형식: 순수 JSON (코드블록/마크다운 금지)
                {"keywords": ["키워드1", "키워드2", ...]}

                ■ 필수 규칙
                1. 키워드는 명사/명사구 단위로 간결하게. 문장 조각/동사/수식어 단독 추출 금지.
                2. 아래 **허용 키워드 목록**에 존재하는 용어가 자막에 실제로 등장하거나 명백히 암시되면 우선 추출 (표기 변형·영문 병기 허용).
                3. 목록에 없는 키워드도 교육적으로 핵심이면 추출 가능 — 단, 고유명사·광고·인사말·진행멘트는 제외.
                4. 중복·동의어는 한 번만. 최대 12개.
                5. 자막이 짧거나 키워드가 없으면 빈 배열 반환: {"keywords": []}

                ■ 허용 키워드 목록(커리큘럼 기준): %s
                """.formatted(allowList);

        LlmRequestDto keywordRequest = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage("강의 주제: " + curriculum.getTopic()
                        + "\n\n자막 원문:\n" + transcriptText)
                .build();

        LlmKeywordExtractionResult extraction = llmService.callJson(keywordRequest, LlmKeywordExtractionResult.class);
        List<String> llmKeywords = extraction.getKeywords();

        if (llmKeywords == null || llmKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        // 커리큘럼 키워드와 교집합 필터링 (대소문자 무시)
        List<String> curriculumKeywords = curriculum.getKeywords();
        if (curriculumKeywords == null || curriculumKeywords.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> curriculumKeywordSet = curriculumKeywords.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // LLM 추출 키워드 중 커리큘럼 키워드에 포함된 것만 반환
        return llmKeywords.stream()
                .filter(k -> curriculumKeywordSet.contains(k.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 필터링된 키워드와 자막 텍스트를 기반으로 LLM에 요약 자료 생성을 요청한다.
     */
    private LlmSummaryResult generateSummary(List<String> keywords, String transcriptText, LearningRoom room) {
        String keywordList = String.join(", ", keywords);
        String subjectContext = (room != null)
                ? String.format("\n학습 주제: %s (수준: %s)\n모든 설명은 반드시 '%s' 학습 맥락에서 작성하세요.", room.getSubject(), room.getLevel(), room.getSubject())
                : "";

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 AI 실시간 학습 도우미입니다.
                """ + subjectContext + """

                학습자가 영상 시청 중 특정 구간을 반복 되감기하거나 오래 일시정지한 것이 감지되었습니다.
                해당 구간의 핵심 개념을 학습자가 빠르게 이해할 수 있도록 풍부한 요약본을 생성하세요.

                ■ 출력 형식: 순수 JSON (코드블록 없이)
                {
                  "summary_title": "요약 제목",
                  "trigger_reason": "트리거 사유 한 줄",
                  "key_points": [
                    {
                      "concept": "개념명 (한글 + 영문)",
                      "icon_letter": "A",
                      "color": "#5b4ccc",
                      "definition": "한 문장 정의",
                      "detail": "2~4가지 포인트를 설명. 여러 항목이 나열되는 경우 불릿(-) 권장. 단일 설명은 문장으로 작성 가능. 줄바꿈으로 항목 구분.",
                      "analogy": "실생활 비유",
                      "exam_tip": "시험 포인트",
                      "checkpoint_question": "자가 점검 질문",
                      "memory_hook": "암기 장치"
                    }
                  ],
                  "comparison_table": {
                    "headers": ["구분", "항목A", "항목B", "항목C"],
                    "rows": [["특징1","값","값","값"]]
                  },
                  "quick_check": ["자가 점검 질문1"],
                  "common_mistakes": ["자주 하는 실수1"],
                  "memory_hooks": ["암기 장치1"],
                  "next_actions": ["다음 학습 행동1"],
                  "related_keywords": ["연관 키워드1"],
                  "difficulty_assessment": "난이도 평가",
                  "study_time_estimate": "예상 학습 시간"
                }

                ■ 필수 규칙:
                1. key_points: 최소 4개 이상. 각 항목에 definition/detail/analogy/exam_tip 필수, checkpoint_question/memory_hook 권장.
                2. comparison_table: 최소 3행 이상, 각 행의 마지막 칸에는 시험 포인트가 드러나도록.
                3. quick_check 3개 이상, common_mistakes 3개 이상, memory_hooks 3개 이상, next_actions 3개 이상.
                4. 모든 설명은 해당 학습 주차 수준에 맞게 작성, 전문 용어는 한글(영문) 병기.
                5. analogy는 반드시 실생활 또는 일상 상황에서 가져올 것.
                6. 가독성: 여러 항목을 나열할 때는 불릿(-)과 줄바꿈 활용 권장. 단일 문장으로 자연스럽게 이어지는 내용은 문장 형식도 허용. 불릿과 문장이 한 필드 안에 섞여도 됨.
                """;

        LlmRequestDto summaryRequest = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage("핵심 키워드: " + keywordList + "\n\n자막 텍스트:\n" + transcriptText)
                .build();

        LlmConceptSummaryResponse raw = llmService.callJson(summaryRequest, LlmConceptSummaryResponse.class);
        return mapConceptSummaryToResult(raw, keywords);
    }

    private LlmSummaryResult mapConceptSummaryToResult(LlmConceptSummaryResponse raw, List<String> keywords) {
        String title = raw != null && raw.getSummaryTitle() != null
                ? raw.getSummaryTitle()
                : "핵심 개념 요약 — " + String.join(", ", keywords);

        List<SummaryItem> items = new ArrayList<>();
        if (raw != null && raw.getKeyPoints() != null) {
            int idx = 0;
            for (LlmConceptSummaryResponse.KeyPoint kp : raw.getKeyPoints()) {
                if (kp == null) continue;
                String label = kp.getIconLetter() != null && !kp.getIconLetter().isBlank()
                        ? kp.getIconLetter()
                        : String.valueOf((char) ('A' + idx));
                StringBuilder desc = new StringBuilder();
                if (kp.getDefinition() != null) desc.append(kp.getDefinition().stripTrailing()).append("\n");
                if (kp.getDetail() != null) desc.append(kp.getDetail().stripTrailing()).append("\n");
                if (kp.getAnalogy() != null) desc.append("💡 비유: ").append(kp.getAnalogy().stripTrailing()).append("\n");
                if (kp.getExamTip() != null) desc.append("📝 시험 포인트: ").append(kp.getExamTip().stripTrailing()).append("\n");
                if (kp.getCheckpointQuestion() != null)
                    desc.append("❓ 자가 점검: ").append(kp.getCheckpointQuestion().stripTrailing()).append("\n");
                if (kp.getMemoryHook() != null)
                    desc.append("🔖 암기 장치: ").append(kp.getMemoryHook().stripTrailing()).append("\n");
                items.add(new SummaryItem(label, kp.getConcept() != null ? kp.getConcept() : "핵심 개념", desc.toString().trim()));
                idx++;
            }
        }

        if (raw != null && raw.getComparisonTable() != null
                && raw.getComparisonTable().getHeaders() != null
                && raw.getComparisonTable().getRows() != null
                && !raw.getComparisonTable().getRows().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            List<String> headers = raw.getComparisonTable().getHeaders();
            sb.append(String.join(" | ", headers)).append("\n");
            sb.append(headers.stream().map(h -> "---").collect(Collectors.joining(" | "))).append("\n");
            for (List<String> row : raw.getComparisonTable().getRows()) {
                sb.append(String.join(" | ", row)).append("\n");
            }
            items.add(new SummaryItem(String.valueOf((char) ('A' + items.size())), "비교 정리표", sb.toString().trim()));
        }

        if (raw != null && raw.getQuickCheck() != null && !raw.getQuickCheck().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (String q : raw.getQuickCheck()) sb.append(i++).append(". ").append(q).append("\n");
            items.add(new SummaryItem(String.valueOf((char) ('A' + items.size())), "자가 점검 질문", sb.toString().trim()));
        }

        return new LlmSummaryResult(title, items);
    }

    /**
     * 단일 targetKeyword와 자막을 기반으로 LLM에 4지선다 퀴즈 1문제 생성을 요청한다.
     * related_keyword는 LLM 반환값을 무시하고 서버에서 targetKeyword로 고정한다.
     */
    private LlmQuizResult generateQuiz(String targetKeyword, String transcriptText, String topic, LearningRoom room) {
        String subjectLine = (room != null) ? "학습 주제: " + room.getSubject() + " (수준: " + room.getLevel() + ")\n" : "";
        String context = transcriptText.isBlank()
                ? subjectLine + "커리큘럼 주제: " + topic + "\n대상 키워드: " + targetKeyword
                : subjectLine + "커리큘럼 주제: " + topic + "\n대상 키워드: " + targetKeyword + "\n\n자막 텍스트:\n" + transcriptText;

        String roomSubject = (room != null) ? room.getSubject() : topic;
        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 돌발 퀴즈 출제 전문가 AI입니다.

                학습 주제 '%s' 강의에서 학습자가 영상 구간을 스킵하거나 2배속으로 빠르게 넘긴 직후, 해당 구간의 핵심 개념을 놓치지 않았는지 즉시 확인하는 4지선다 문제를 만듭니다.
                반드시 '%s' 학습 맥락에서 출제하세요. 다른 과목이나 분야의 내용으로 출제하지 마세요.

                ■ 출력 형식: 순수 JSON (코드블록 없이)
                {
                  "questions": [
                    {
                      "question": "4지선다 문항",
                      "options": [
                        {"label":"A","text":"보기1"},
                        {"label":"B","text":"보기2"},
                        {"label":"C","text":"보기3"},
                        {"label":"D","text":"보기4"}
                      ],
                      "answer": "A/B/C/D",
                      "explanation": "정답 해설과 오답 포인트"
                    }
                  ]
                }

                ■ 필수 규칙:
                1. 문제는 반드시 대상 키워드 '%s' 하나에 집중해서 출제. 다른 개념을 중심으로 만들지 마세요.
                2. 모든 문항은 4지선다 객관식. 참/거짓형 금지.
                3. options는 항상 정확히 4개, label은 A/B/C/D.
                4. explanation은 왜 정답이고 왜 다른 보기가 오답인지 구체적으로 설명.
                5. 전문 용어는 한글(영문) 병기.
                6. 자막 텍스트가 있으면 해당 구간 내용을 반영. 없으면 '%s' 개념 자체로 출제.
                """, roomSubject, roomSubject, targetKeyword, targetKeyword);

        LlmRequestDto quizRequest = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(context)
                .build();

        LlmPopupQuizResponse raw = llmService.callJson(quizRequest, LlmPopupQuizResponse.class);
        // related_keyword는 커리큘럼 키워드 목록 외 값이 오는 경우를 방지하기 위해 서버에서 고정
        return mapPopupQuizToResult(raw, targetKeyword);
    }

    private LlmQuizResult mapPopupQuizToResult(LlmPopupQuizResponse raw, String targetKeyword) {
        LlmPopupQuizResponse.Question first = raw != null && raw.getQuestions() != null && !raw.getQuestions().isEmpty()
                ? raw.getQuestions().get(0)
                : null;
        if (first == null) {
            throw new IllegalStateException("LLM이 돌발 퀴즈 문항을 반환하지 않았습니다");
        }

        List<com.moai.backend.domain.quiz.entity.QuizOption> options = new ArrayList<>();
        if (first.getOptions() != null) {
            int idx = 0;
            for (LlmPopupQuizResponse.Option opt : first.getOptions()) {
                if (opt == null) continue;
                String label = (opt.getLabel() != null && !opt.getLabel().isBlank())
                        ? opt.getLabel().trim().toUpperCase()
                        : String.valueOf((char) ('A' + idx));
                options.add(new com.moai.backend.domain.quiz.entity.QuizOption(label, opt.getText()));
                idx++;
            }
        }
        while (options.size() < 4) {
            options.add(new com.moai.backend.domain.quiz.entity.QuizOption(
                    String.valueOf((char) ('A' + options.size())), "보기 " + (options.size() + 1)));
        }
        if (options.size() > 4) {
            options = options.subList(0, 4);
        }

        String answer = first.getAnswer() != null ? first.getAnswer().trim().toUpperCase() : "A";
        // LLM 반환값 무시 — 항상 서버에서 선택한 커리큘럼 키워드로 고정
        return new LlmQuizResult(first.getQuestion(), options, answer, targetKeyword);
    }

    /**
     * 필터링된 키워드마다 UserKeyword를 upsert한다.
     * 이미 존재하면 weaknessCount를 증가시키고, 없으면 새로 생성한다.
     */
    private void upsertWeaknessKeywords(User user, LearningRoom room,
                                         WeeklyCurriculum curriculum, List<String> keywords) {
        for (String keyword : keywords) {
            Optional<UserKeyword> existing = userKeywordRepository
                    .findByUserIdAndRoomIdAndKeyword(user.getId(), room.getId(), keyword);

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
     * 초 단위 시간을 "영상 구간 MM:SS" 형식 문자열로 변환한다.
     */
    private String formatVideoSegment(double sec) {
        int totalSec = (int) sec;
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        return String.format("영상 구간 %d:%02d", minutes, seconds);
    }

    // ──────────────────────────────────────────────
    // 결과 레코드
    // ──────────────────────────────────────────────

    public record MaterialProcessResult(List<String> extractedKeywords, String materialId) {}

    public record QuizProcessResult(String quizId, String questionId) {}

    // ──────────────────────────────────────────────
    // LLM 응답 DTO (내부 클래스)
    // ──────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    static class LlmSingleKeywordResult {
        @JsonProperty("keyword")
        private String keyword;
    }

    @Getter
    @NoArgsConstructor
    static class LlmConceptSummaryResponse {
        @JsonProperty("summary_title")
        private String summaryTitle;

        @JsonProperty("trigger_reason")
        private String triggerReason;

        @JsonProperty("key_points")
        private List<KeyPoint> keyPoints;

        @JsonProperty("comparison_table")
        private ComparisonTable comparisonTable;

        @JsonProperty("quick_check")
        private List<String> quickCheck;

        @JsonProperty("common_mistakes")
        private List<String> commonMistakes;

        @JsonProperty("memory_hooks")
        private List<String> memoryHooks;

        @JsonProperty("next_actions")
        private List<String> nextActions;

        @JsonProperty("related_keywords")
        private List<String> relatedKeywords;

        @JsonProperty("difficulty_assessment")
        private String difficultyAssessment;

        @JsonProperty("study_time_estimate")
        private String studyTimeEstimate;

        @Getter
        @NoArgsConstructor
        static class KeyPoint {
            private String concept;

            @JsonProperty("icon_letter")
            private String iconLetter;

            private String color;
            private String definition;
            private String detail;
            private String analogy;

            @JsonProperty("exam_tip")
            private String examTip;

            @JsonProperty("checkpoint_question")
            private String checkpointQuestion;

            @JsonProperty("memory_hook")
            private String memoryHook;
        }

        @Getter
        @NoArgsConstructor
        static class ComparisonTable {
            private List<String> headers;
            private List<List<String>> rows;
        }
    }

    @Getter
    @NoArgsConstructor
    static class LlmPopupQuizResponse {
        @JsonProperty("quiz_type")
        private String quizType;

        @JsonProperty("trigger_message")
        private String triggerMessage;

        private List<Question> questions;

        @JsonProperty("mini_review")
        private List<String> miniReview;

        @JsonProperty("follow_up_missions")
        private List<String> followUpMissions;

        @JsonProperty("related_keywords")
        private List<String> relatedKeywords;

        @Getter
        @NoArgsConstructor
        static class Question {
            private Integer order;

            @JsonProperty("question_type")
            private String questionType;

            private String question;
            private List<Option> options;
            private String answer;
            private String explanation;

            @JsonProperty("related_concept")
            private String relatedConcept;

            @JsonProperty("related_keyword")
            private String relatedKeyword;

            @JsonProperty("follow_up_tip")
            private String followUpTip;

            @JsonProperty("trap_reason")
            private String trapReason;
        }

        @Getter
        @NoArgsConstructor
        static class Option {
            private String label;
            private String text;
        }
    }
}
