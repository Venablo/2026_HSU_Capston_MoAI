package com.moai.backend.domain.eventlog.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.eventlog.dto.LlmKeywordExtractionResult;
import com.moai.backend.domain.eventlog.dto.LlmQuizResult;
import com.moai.backend.domain.eventlog.dto.LlmSummaryResult;
import com.moai.backend.domain.eventlog.entity.LearningEventLog;
import com.moai.backend.domain.eventlog.repository.LearningEventLogRepository;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.material.entity.CustomMaterial;
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

    // 되감기 구간 자막 조회 범위(초): 되감기 지점부터 앞으로 30초
    private static final int REWIND_LOOKAHEAD_SEC = 30;

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
    public RewindProcessResult processRewindPattern(User user, LearningRoom room,
                                                     WeeklyCurriculum curriculum,
                                                     String videoId, double rewindTargetSec,
                                                     String payloadJson) {
        // 1. 이벤트 로그 저장
        saveEventLog(user, curriculum, videoId, "video_rewind", payloadJson);

        // 2. 되감기 지점부터 30초 앞 구간과 겹치는 자막 조회 (start_sec <= toSec AND end_sec >= fromSec)
        BigDecimal fromSec = BigDecimal.valueOf(rewindTargetSec);
        BigDecimal toSec = BigDecimal.valueOf(rewindTargetSec + REWIND_LOOKAHEAD_SEC);
        List<VideoTranscript> transcripts = transcriptRepository
                .findByCurriculumIdAndVideoIdAndStartSecLessThanEqualAndEndSecGreaterThanEqualOrderByChunkIndex(
                        curriculum.getId(), videoId, toSec, fromSec);

        String transcriptText = joinTranscriptTexts(transcripts);
        if (transcriptText.isBlank()) {
            log.warn("되감기 구간 자막 없음 — curriculum={}, video={}, sec={}",
                    curriculum.getId(), videoId, rewindTargetSec);
            return new RewindProcessResult(Collections.emptyList(), null);
        }

        // 3. LLM 키워드 추출 → 커리큘럼 키워드와 교집합 필터링
        List<String> filteredKeywords = extractAndFilterKeywords(transcriptText, curriculum);

        if (filteredKeywords.isEmpty()) {
            log.info("되감기 구간에서 커리큘럼 키워드와 일치하는 키워드 없음");
            return new RewindProcessResult(Collections.emptyList(), null);
        }

        // 4. LLM 요약 자료 생성 (필터링된 키워드 기반)
        LlmSummaryResult summary = generateSummary(filteredKeywords, transcriptText);

        // 5. CustomMaterial 저장
        String videoSegment = formatVideoSegment(rewindTargetSec);
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

        return new RewindProcessResult(filteredKeywords, material.getId());
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

        // 4. LLM 4지선다 퀴즈 1문제 생성 (필터링된 키워드 + 자막 기반)
        LlmQuizResult quizResult = generateQuiz(filteredKeywords, transcriptText, curriculum.getTopic());

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
     * LLM으로 자막에서 키워드를 추출한 뒤,
     * 커리큘럼에 등록된 키워드와 교집합하여 관련 키워드만 필터링한다.
     */
    private List<String> extractAndFilterKeywords(String transcriptText, WeeklyCurriculum curriculum) {
        LlmRequestDto keywordRequest = LlmRequestDto.builder()
                .systemPrompt("당신은 교육 콘텐츠 분석 전문가입니다. "
                        + "주어진 자막 텍스트에서 학습과 관련된 핵심 키워드를 추출하세요. "
                        + "JSON 형식으로 응답하세요: {\"keywords\": [\"키워드1\", \"키워드2\", ...]}")
                .userMessage("다음 자막에서 핵심 키워드를 추출하세요:\n\n" + transcriptText)
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
    private LlmSummaryResult generateSummary(List<String> keywords, String transcriptText) {
        String keywordList = String.join(", ", keywords);

        LlmRequestDto summaryRequest = LlmRequestDto.builder()
                .systemPrompt("당신은 교육 콘텐츠 요약 전문가입니다. "
                        + "주어진 키워드와 자막을 기반으로 학습 요약 자료를 생성하세요. "
                        + "JSON 형식으로 응답하세요: "
                        + "{\"title\": \"요약 제목\", \"summaryItems\": ["
                        + "{\"label\": \"A\", \"title\": \"항목 제목\", \"desc\": \"상세 설명\"}, ...]}")
                .userMessage("키워드: " + keywordList + "\n\n자막 텍스트:\n" + transcriptText)
                .build();

        return llmService.callJson(summaryRequest, LlmSummaryResult.class);
    }

    /**
     * 필터링된 키워드, 자막 텍스트, 주차 주제를 기반으로 LLM에 4지선다 퀴즈 1문제 생성을 요청한다.
     */
    private LlmQuizResult generateQuiz(List<String> keywords, String transcriptText, String topic) {
        String keywordList = keywords.isEmpty() ? topic : String.join(", ", keywords);
        String context = transcriptText.isBlank()
                ? "주제: " + topic
                : "키워드: " + keywordList + "\n\n자막 텍스트:\n" + transcriptText;

        LlmRequestDto quizRequest = LlmRequestDto.builder()
                .systemPrompt("당신은 교육 퀴즈 출제 전문가입니다. "
                        + "주어진 키워드와 자막을 기반으로 4지선다 객관식 문제 1개를 생성하세요. "
                        + "JSON 형식으로 응답하세요: "
                        + "{\"question\": \"문제 본문\", \"options\": ["
                        + "{\"label\": \"A\", \"text\": \"선택지1\"}, "
                        + "{\"label\": \"B\", \"text\": \"선택지2\"}, "
                        + "{\"label\": \"C\", \"text\": \"선택지3\"}, "
                        + "{\"label\": \"D\", \"text\": \"선택지4\"}], "
                        + "\"answer\": \"정답 라벨(A/B/C/D)\", "
                        + "\"relatedKeyword\": \"관련 키워드\"}")
                .userMessage(context)
                .build();

        return llmService.callJson(quizRequest, LlmQuizResult.class);
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

    public record RewindProcessResult(List<String> extractedKeywords, String materialId) {}

    public record QuizProcessResult(String quizId, String questionId) {}
}
