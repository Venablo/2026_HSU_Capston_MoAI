package com.moai.backend.domain.quiz.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.domain.quiz.dto.FinalQuizResponseDto;
import com.moai.backend.domain.quiz.dto.FinalQuizSubmitRequestDto;
import com.moai.backend.domain.quiz.dto.FinalQuizSubmitResponseDto;
import com.moai.backend.domain.quiz.dto.InstantQuizResponseDto;
import com.moai.backend.domain.quiz.dto.LlmEssayGradingResult;
import com.moai.backend.domain.quiz.dto.LlmFinalQuizResult;
import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptRequestDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptResponseDto;
import com.moai.backend.domain.quiz.dto.QuizReportResponseDto;
import com.moai.backend.domain.quiz.entity.Quiz;
import com.moai.backend.domain.quiz.entity.QuizAttempt;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.entity.QuizReport;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizReportRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private static final BigDecimal FINAL_QUIZ_COMPLETION_WEIGHT = BigDecimal.valueOf(30);
    private static final BigDecimal MAX_COMPLETION = BigDecimal.valueOf(100);

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizReportRepository quizReportRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private QuizService self;

    public List<QuizAttemptListResponseDto> getQuizAttempts(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        // 해당 주차가 학습실에 속하는지 검증
        weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        return quizAttemptRepository.findByUserIdAndQuiz_CurriculumIdOrderByAttemptedAtDesc(
                user.getId(), weekId
        ).stream()
                .map(QuizAttemptListResponseDto::from)
                .toList();
    }

    public QuizAttemptDetailResponseDto getQuizAttemptDetail(String email, String attemptId) {
        User user = findUserByEmail(email);

        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));

        // 본인의 응시 기록만 조회 가능
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }

        return QuizAttemptDetailResponseDto.from(attempt);
    }

    // ──────────────────────────────────────────────
    // 돌발 퀴즈 1문제 조회
    // ──────────────────────────────────────────────

    /**
     * 특정 주차의 돌발 퀴즈(multiple_popup) 중 가장 최근 생성된 1문제를 조회한다.
     */
    public InstantQuizResponseDto getInstantQuiz(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // 가장 최근 생성된 돌발 퀴즈(multiple_popup) 조회
        Quiz quiz = quizRepository.findTopByCurriculumIdAndQuizTypeOrderByCreatedAtDesc(weekId, "multiple_popup")
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));

        // 해당 퀴즈의 첫 번째 문항 조회 (돌발 퀴즈는 1문제)
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrder(quiz.getId());
        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }

        return InstantQuizResponseDto.from(questions.get(0));
    }

    // ──────────────────────────────────────────────
    // 퀴즈 정답 제출
    // ──────────────────────────────────────────────

    /**
     * 퀴즈 정답 제출 처리 흐름:
     * 1) 문항 조회 → 정답 비교 → is_correct 판별
     * 2) LLM → AI 해설 생성
     * 3) QuizAttempt 저장
     * 4) 오답 시 UserKeyword 약점 upsert
     * 5) 오답 시 rewindToSec 포함하여 응답
     */
    @Transactional
    public QuizAttemptResponseDto submitQuizAttempt(String email, QuizAttemptRequestDto request) {
        User user = findUserByEmail(email);

        // 1. 문항 조회 및 정답 판별
        QuizQuestion question = quizQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));

        // 제출한 답과 정답을 대소문자 무시하고 비교
        boolean isCorrect = question.getAnswer() != null
                && question.getAnswer().equalsIgnoreCase(request.getSelected());

        // 2. LLM으로 AI 해설 생성
        String aiExplanation = generateAiExplanation(question, request.getSelected(), isCorrect);

        // 3. QuizAttempt 저장
        QuizAttempt attempt = QuizAttempt.builder()
                .question(question)
                .quiz(quiz)
                .user(user)
                .selected(request.getSelected())
                .isCorrect(isCorrect)
                .aiExplanation(aiExplanation)
                .build();
        quizAttemptRepository.save(attempt);

        // 4. 오답 시 UserKeyword 약점 누적 (relatedKeyword 기반 upsert)
        Integer rewindToSec = null;
        if (!isCorrect && question.getRelatedKeyword() != null) {
            upsertWeaknessKeyword(user, quiz, question.getRelatedKeyword());

            // 5. 오답 시 Quiz.rewindToSec 조회하여 되감기 지점 반환
            rewindToSec = quiz.getRewindToSec();
        }

        String relatedVideoId = null;
        if (quiz.getCurriculum() != null && quiz.getCurriculum().getResources() != null) {
            relatedVideoId = quiz.getCurriculum().getResources().stream()
                    .filter(r -> "youtube".equals(r.getType()))
                    .map(com.moai.backend.domain.curriculum.entity.CurriculumResource::getVideoId)
                    .findFirst()
                    .orElse(null);
        }

        return QuizAttemptResponseDto.builder()
                .attemptId(attempt.getId())
                .isCorrect(isCorrect)
                .correctAnswer(question.getAnswer())
                .aiExplanation(aiExplanation)
                .relatedVideoId(relatedVideoId)
                .relatedTimestamp(rewindToSec)
                .rewindToSec(rewindToSec)
                .build();
    }

    // ──────────────────────────────────────────────
    // 파이널 퀴즈 조회 (없으면 LLM 자동 생성)
    // ──────────────────────────────────────────────

    @Transactional
    public FinalQuizResponseDto getFinalQuiz(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // completionRate >= 70 검증 (영상 시청 40% + 거꾸로 학습 30%)
        if (curriculum.getCompletionRate().compareTo(BigDecimal.valueOf(70)) < 0) {
            throw new CustomException(ErrorCode.FINAL_QUIZ_NOT_READY);
        }

        // 기존 weekly 퀴즈가 있으면 반환
        Optional<Quiz> existingQuiz = quizRepository.findByCurriculumIdAndQuizType(weekId, "weekly");
        if (existingQuiz.isPresent()) {
            Quiz quiz = existingQuiz.get();
            List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrder(quiz.getId());
            return buildFinalQuizResponse(quiz, questions);
        }

        // 없으면 LLM으로 서술형 5문제 생성
        return generateFinalQuiz(curriculum);
    }

    private FinalQuizResponseDto generateFinalQuiz(WeeklyCurriculum curriculum) {
        String keywordsStr = String.join(", ", curriculum.getKeywords());

        String userMessage = String.format(
                "{\"curriculum_topic\":\"%s\",\"week_number\":%d,\"key_concepts\":[%s]}",
                curriculum.getTopic(), (int) curriculum.getWeekNumber(),
                curriculum.getKeywords().stream().map(k -> "\"" + k.replace("\"", "\\\"") + "\"").collect(java.util.stream.Collectors.joining(","))
        );

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 주차 마무리 퀴즈 출제 AI입니다.

                주차 학습 완료 후 이해도를 종합 검증하는 서술형 5문항 퀴즈 세트를 생성하세요.

                ■ 출력: 순수 JSON (코드블록 없이)
                {
                  "quiz_title": "Week N 파이널 퀴즈 — 주제명",
                  "total_score": 100,
                  "time_limit_minutes": 15,
                  "questions": [
                    {
                      "order": 1,
                      "question": "서술형 문제 (실생활 비유 요구 또는 개념 비교 요구. 단순 정의 나열 금지)",
                      "related_keyword": "핵심 키워드",
                      "hint": "💡 힌트: 구체적 사고 방향 안내",
                      "max_score": 20,
                      "max_length": 500,
                      "scoring_rubric": "필수키워드(8점): [키워드1, 키워드2] + 비유적절성(6점) + 논리구성(6점)",
                      "sample_answer_keywords": ["모범답안 키워드1", "키워드2", "키워드3"],
                      "difficulty": "하/중/상"
                    }
                  ],
                  "study_guide": "퀴즈 전 복습 가이드"
                }

                ■ 필수 규칙:
                1. 반드시 5문항, 각 20점 = 총 100점
                2. 문제 유형 다양화: 정의 설명, 비유 적용, 비교 분석, 시나리오 적용, 오류 찾기
                3. hint는 답변 방향만 제시 (답 자체는 노출 금지)
                4. scoring_rubric에 구체적 배점 명시
                5. sample_answer_keywords는 AI 채점 시 매칭할 키워드 목록 (반드시 key_concepts에 있는 단어)
                6. difficulty를 골고루 분배 (하1 + 중2 + 상2 권장)
                7. related_keyword는 반드시 입력된 key_concepts 목록 안에서 선택
                8. max_length는 300~500 사이 정수
                """;

        LlmRequestDto llmRequest = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        LlmFinalQuizResult result = llmService.callJson(llmRequest, LlmFinalQuizResult.class);

        // Quiz INSERT
        Quiz quiz = Quiz.builder()
                .curriculum(curriculum)
                .quizType("weekly")
                .title("Week " + curriculum.getWeekNumber() + " 파이널 퀴즈")
                .build();
        quizRepository.save(quiz);

        // QuizQuestion INSERT × 5
        List<QuizQuestion> questions = new java.util.ArrayList<>();
        short order = 1;
        for (LlmFinalQuizResult.QuestionData qd : result.getQuestions()) {
            QuizQuestion question = QuizQuestion.builder()
                    .quiz(quiz)
                    .questionType("essay")
                    .question(qd.getQuestion())
                    .questionOrder(order++)
                    .relatedKeyword(qd.getRelatedKeyword())
                    .maxLength(qd.getMaxLength() != null ? qd.getMaxLength() : (short) 500)
                    .tip(qd.getTip())
                    .build();
            questions.add(quizQuestionRepository.save(question));
        }

        return buildFinalQuizResponse(quiz, questions);
    }

    private FinalQuizResponseDto buildFinalQuizResponse(Quiz quiz, List<QuizQuestion> questions) {
        List<FinalQuizResponseDto.QuestionItem> items = questions.stream()
                .map(FinalQuizResponseDto.QuestionItem::from)
                .toList();
        return new FinalQuizResponseDto(quiz.getId(), quiz.getTitle(), items);
    }

    // ──────────────────────────────────────────────
    // 파이널 퀴즈 제출 (비동기 채점)
    // ──────────────────────────────────────────────

    @Transactional
    public FinalQuizSubmitResponseDto submitFinalQuiz(String email, String roomId,
                                                       String weekId,
                                                       FinalQuizSubmitRequestDto request) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // 중복 제출 검사
        if (quizReportRepository.findByUserIdAndCurriculumId(user.getId(), weekId).isPresent()) {
            throw new CustomException(ErrorCode.FINAL_QUIZ_ALREADY_SUBMITTED);
        }

        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));

        // QuizReport INSERT (status="analyzing")
        QuizReport report = QuizReport.builder()
                .quiz(quiz)
                .user(user)
                .curriculum(curriculum)
                .finalScore(BigDecimal.ZERO)
                .status("analyzing")
                .estimatedSec((short) 15)
                .build();
        quizReportRepository.save(report);

        // 비동기 채점 시작 — self 프록시 경유로 @Async 활성화
        self.gradeFinalQuizAsync(report.getId(), quiz.getId(), user.getId(),
                room.getId(), curriculum.getId(), request.getAnswers());

        return new FinalQuizSubmitResponseDto(report.getId(), "analyzing", (short) 15);
    }

    @Async
    @Transactional
    public void gradeFinalQuizAsync(String reportId, String quizId, String userId,
                                     String roomId, String curriculumId,
                                     List<FinalQuizSubmitRequestDto.AnswerItem> answers) {
        try {
            QuizReport report = quizReportRepository.findById(reportId)
                    .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));
            Quiz quiz = quizRepository.findById(quizId)
                    .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_NOT_FOUND));
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            LearningRoom room = learningRoomRepository.findById(roomId)
                    .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
            WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(curriculumId)
                    .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

            int totalScore = 0;
            List<Map<String, Object>> questionResults = new ArrayList<>();
            // radarData 생성용 요약 수집
            StringBuilder gradingSummary = new StringBuilder();

            for (FinalQuizSubmitRequestDto.AnswerItem answerItem : answers) {
                QuizQuestion question = quizQuestionRepository.findById(answerItem.getQuestionId())
                        .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

                // LLM 서술형 채점 (커리큘럼 키워드 제약)
                LlmEssayGradingResult grading = gradeEssayQuestion(
                        question, answerItem.getAnswer(), curriculum.getKeywords());

                boolean isCorrect = grading.getScore() >= 12;

                // QuizAttempt INSERT
                QuizAttempt attempt = QuizAttempt.builder()
                        .question(question)
                        .quiz(quiz)
                        .user(user)
                        .selected(answerItem.getAnswer())
                        .isCorrect(isCorrect)
                        .aiExplanation(grading.getAiComment())
                        .build();
                quizAttemptRepository.save(attempt);

                // UserKeyword UPSERT
                if (isCorrect) {
                    resolveAndPromoteKeywords(user, room, curriculum, grading.getGainedKeywords());
                } else {
                    upsertWeaknessKeywords(user, room, curriculum, grading.getWeaknessKeywords());
                }

                totalScore += grading.getScore();

                // questions JSON 항목 구성
                Map<String, Object> questionResult = new LinkedHashMap<>();
                questionResult.put("order", question.getQuestionOrder());
                questionResult.put("question", question.getQuestion());
                questionResult.put("score", grading.getScore());
                questionResult.put("maxScore", 20);
                questionResult.put("isCorrect", isCorrect);
                questionResult.put("myAnswer", answerItem.getAnswer());
                questionResult.put("gainedKeywords", grading.getGainedKeywords());
                questionResult.put("missingKeywords", grading.getWeaknessKeywords());
                questionResult.put("aiComment", grading.getAiComment());
                questionResults.add(questionResult);

                // radarData 생성용 요약
                gradingSummary.append(String.format(
                        "문항%d (키워드: %s) — 점수: %d/20, 강점: %s, 약점: %s\n",
                        question.getQuestionOrder(), question.getRelatedKeyword(),
                        grading.getScore(), grading.getGainedKeywords(), grading.getWeaknessKeywords()
                ));
            }

            // LLM으로 radarData 생성
            String radarDataJson = generateRadarData(gradingSummary.toString(), totalScore);

            // QuizReport 완료 처리
            BigDecimal finalScore = BigDecimal.valueOf(totalScore);
            String questionsJson = objectMapper.writeValueAsString(questionResults);
            report.complete(finalScore, radarDataJson, questionsJson);

            // 주차 진척도 +30% (100% 초과 방지) 및 학습실 재계산
            updateCompletionRates(curriculum, room);

        } catch (JsonProcessingException e) {
            log.error("파이널 퀴즈 리포트 JSON 직렬화 실패", e);
        } catch (Exception e) {
            log.error("파이널 퀴즈 비동기 채점 실패: reportId={}", reportId, e);
        }
    }

    private LlmEssayGradingResult gradeEssayQuestion(QuizQuestion question, String studentAnswer,
                                                      List<String> curriculumKeywords) {
        String keywordsStr = String.join(", ", curriculumKeywords);

        String userMessage = String.format(
                "{\"question\":%s,\"related_keyword\":%s,\"max_score\":20,\"student_answer\":%s,\"curriculum_keywords\":[%s]}",
                quoteJson(question.getQuestion()),
                quoteJson(question.getRelatedKeyword()),
                quoteJson(studentAnswer),
                curriculumKeywords.stream().map(this::quoteJson).collect(java.util.stream.Collectors.joining(","))
        );

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 AI 채점 전문가입니다.

                학습자의 서술형 답변을 분석하여 상세한 채점 결과와 피드백을 제공하세요.

                ■ 출력: 순수 JSON (코드블록 없이)
                {
                  "score": 0~20,
                  "max_score": 20,
                  "grade": "A+/A/B+/B/C+/C/D/F",
                  "overall_feedback": "종합 피드백 (3~4문장. 칭찬→부족한 점→개선 방향 순)",
                  "keyword_analysis": [
                    {"keyword":"필수키워드","found":true,"in_context":"해당 키워드 사용 문맥","score_contribution":4},
                    {"keyword":"빠진키워드","found":false,"suggestion":"보완 방법","score_contribution":0}
                  ],
                  "accuracy_score": {"score":0,"max":8,"detail":"정확성 평가"},
                  "depth_score": {"score":0,"max":6,"detail":"깊이/비유 평가"},
                  "logic_score": {"score":0,"max":6,"detail":"논리 구성 평가"},
                  "correct_answer_summary": "모범 답안 요약 (3~5문장)",
                  "improvement_tips": ["구체적 개선 팁1","팁2"],
                  "gained_keywords": ["학생이 잘 이해한 키워드"],
                  "weakness_keywords": ["학생이 부족한 키워드"]
                }

                ■ 필수 규칙:
                1. scoring_rubric이 있다면 그 기준에 따라 엄격하되 공정하게 채점
                2. keyword_analysis에서 각 필수 키워드의 등장 여부와 맥락 분석
                3. 부분 점수 인정 (키워드는 있지만 설명이 부정확한 경우 등)
                4. correct_answer_summary로 학습자가 부족한 부분을 보완할 수 있게 안내
                5. 격려와 건설적 피드백 균형
                6. gained_keywords, weakness_keywords 는 반드시 입력된 curriculum_keywords 목록에서만 선택. 목록 외 임의 생성 금지.
                   [허용 키워드] %s
                """.formatted(keywordsStr);

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        LlmRichGradingResponse raw = llmService.callJson(request, LlmRichGradingResponse.class);
        return mapRichGradingToResult(raw);
    }

    private LlmEssayGradingResult mapRichGradingToResult(LlmRichGradingResponse raw) {
        if (raw == null) {
            throw new IllegalStateException("채점 응답이 비어있습니다");
        }

        StringBuilder comment = new StringBuilder();
        if (raw.getOverallFeedback() != null) comment.append(raw.getOverallFeedback()).append("\n\n");
        if (raw.getAccuracyScore() != null && raw.getAccuracyScore().getDetail() != null) {
            comment.append("🎯 정확성(").append(nn(raw.getAccuracyScore().getScore()))
                    .append("/").append(nn(raw.getAccuracyScore().getMax())).append("): ")
                    .append(raw.getAccuracyScore().getDetail()).append("\n");
        }
        if (raw.getDepthScore() != null && raw.getDepthScore().getDetail() != null) {
            comment.append("🔬 깊이(").append(nn(raw.getDepthScore().getScore()))
                    .append("/").append(nn(raw.getDepthScore().getMax())).append("): ")
                    .append(raw.getDepthScore().getDetail()).append("\n");
        }
        if (raw.getLogicScore() != null && raw.getLogicScore().getDetail() != null) {
            comment.append("🧩 논리(").append(nn(raw.getLogicScore().getScore()))
                    .append("/").append(nn(raw.getLogicScore().getMax())).append("): ")
                    .append(raw.getLogicScore().getDetail()).append("\n");
        }
        if (raw.getCorrectAnswerSummary() != null) {
            comment.append("\n📘 모범 답안 요약\n").append(raw.getCorrectAnswerSummary()).append("\n");
        }
        if (raw.getImprovementTips() != null && !raw.getImprovementTips().isEmpty()) {
            comment.append("\n💡 개선 팁\n");
            for (String tip : raw.getImprovementTips()) comment.append("- ").append(tip).append("\n");
        }

        int score = raw.getScore() != null ? raw.getScore() : 0;
        List<String> gained = raw.getGainedKeywords() != null ? raw.getGainedKeywords() : List.of();
        List<String> weak = raw.getWeaknessKeywords() != null ? raw.getWeaknessKeywords() : List.of();

        return new LlmEssayGradingResult(score, gained, weak, comment.toString().trim());
    }

    private int nn(Integer v) { return v != null ? v : 0; }

    private String quoteJson(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String generateRadarData(String gradingSummary, int totalScore) {
        String userMessage = String.format(
                "[총점] %d/100\n\n[문항별 채점 결과]\n%s",
                totalScore, gradingSummary
        );

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 학습 분석 AI입니다.

                5문항 파이널 퀴즈 채점 결과를 종합해 4개 역량 축의 점수(0~100)를 산출하세요.

                ■ 출력: 순수 JSON (코드블록 없이)
                {
                  "개념이해도": 0~100,
                  "응용력": 0~100,
                  "논리력": 0~100,
                  "키워드적중률": 0~100
                }

                ■ 규칙:
                1. 개념이해도: 각 문항의 정확성과 keyword_analysis의 found 비율 기반.
                2. 응용력: 비유/시나리오 적용 문항의 점수 비중.
                3. 논리력: 논리 구성 점수의 평균.
                4. 키워드적중률: 필수 키워드 중 실제 등장한 비율 × 100.
                5. 총점(%d/100)과 문항별 점수를 주된 근거로 삼고, 편차가 큰 경우 낮은 항목을 더 크게 반영.
                """.formatted(totalScore);

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        return llmService.call(request).getContent().trim();
    }

    private void resolveAndPromoteKeywords(User user, LearningRoom room,
                                            WeeklyCurriculum curriculum,
                                            List<String> gainedKeywords) {
        if (gainedKeywords == null || gainedKeywords.isEmpty()) return;

        for (String keyword : gainedKeywords) {
            userKeywordRepository
                    .findByUserIdAndRoomIdAndKeywordAndKeywordType(
                            user.getId(), room.getId(), keyword, "weakness")
                    .ifPresent(uk -> {
                        if (!uk.getIsResolved()) {
                            uk.resolve();
                        }
                    });

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

    private void upsertWeaknessKeywords(User user, LearningRoom room,
                                         WeeklyCurriculum curriculum,
                                         List<String> weaknessKeywords) {
        if (weaknessKeywords == null || weaknessKeywords.isEmpty()) return;

        for (String keyword : weaknessKeywords) {
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

    private void updateCompletionRates(WeeklyCurriculum curriculum, LearningRoom room) {
        BigDecimal newRate = curriculum.getCompletionRate().add(FINAL_QUIZ_COMPLETION_WEIGHT);
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

    // ──────────────────────────────────────────────
    // AI 분석 리포트 조회
    // ──────────────────────────────────────────────

    public QuizReportResponseDto getQuizReport(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        QuizReport report = quizReportRepository.findByUserIdAndCurriculumId(user.getId(), weekId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_REPORT_NOT_FOUND));

        if ("analyzing".equals(report.getStatus())) {
            return QuizReportResponseDto.builder()
                    .status("analyzing")
                    .estimatedSec(report.getEstimatedSec())
                    .build();
        }

        // completed — radarData/questions는 raw JSON이므로 Object로 역직렬화
        try {
            Object radarData = objectMapper.readValue(report.getRadarData(), Object.class);
            Object questions = objectMapper.readValue(report.getQuestions(), Object.class);

            return QuizReportResponseDto.builder()
                    .status(report.getStatus())
                    .finalScore(report.getFinalScore())
                    .radarData(radarData)
                    .questions(questions)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("QuizReport JSON 역직렬화 실패: reportId={}", report.getId(), e);
            throw new CustomException(ErrorCode.LLM_RESPONSE_PARSE_ERROR);
        }
    }

    /**
     * LLM을 통해 퀴즈 문항에 대한 AI 해설을 생성한다.
     * 선택지 라벨(A/B/C/D)과 텍스트를 모두 포함하여
     * LLM이 라벨과 내용을 혼동하지 않도록 한다.
     */
    private String generateAiExplanation(QuizQuestion question, String selected, boolean isCorrect) {
        // 선택지 전체를 "A: 텍스트" 형식으로 나열
        StringBuilder optionsText = new StringBuilder();
        if (question.getOptions() != null) {
            for (var opt : question.getOptions()) {
                optionsText.append(opt.getLabel()).append(": ").append(opt.getText()).append("\n");
            }
        }

        // 학생 답변과 정답을 "라벨: 텍스트" 형태로 명시하여 혼동 방지
        String selectedText = findOptionText(question, selected);
        String correctText = findOptionText(question, question.getAnswer());
        String correctness = isCorrect ? "정답" : "오답";

        String userMessage = String.format(
                "[문제]\n%s\n\n[선택지]\n%s\n[학생 답변] %s: %s\n[정답] %s: %s\n[결과] %s\n[관련 키워드] %s",
                question.getQuestion(), optionsText,
                selected, selectedText,
                question.getAnswer(), correctText,
                correctness, question.getRelatedKeyword()
        );

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 돌발 OX/객관식 퀴즈 해설 튜터 AI입니다.

                역할: 학생이 방금 응답한 문항에 대해 즉시 이해를 돕는 짧은 해설을 제공합니다.

                ■ 출력 형식: 순수 텍스트 (마크다운/코드블록/JSON 금지), 한국어 존댓말, 3~4문장.

                ■ 필수 규칙
                1. 선택지 라벨(A/B/C/D)은 단순 식별자이며 내용과 무관함. 반드시 "A: 선택지 텍스트" 형태로 라벨과 원문을 함께 인용.
                2. 정답일 때: 학생의 정답 선택지가 왜 맞는지 핵심 근거를 제시하고, 관련 키워드의 의미를 1문장으로 복습.
                3. 오답일 때: (a) 학생이 고른 선택지가 왜 틀렸는지 오개념을 짚어주고, (b) 정답 선택지가 왜 옳은지 비교 포인트를 명시.
                4. 관련 키워드를 "한글(영문)" 형태로 1회만 병기 (예: 트랜잭션(Transaction)).
                5. 학생을 질책하거나 평가절하하지 말 것 — 오답도 학습 기회라는 전제로 격려 문장을 마지막에 1줄 포함.
                6. 문항 밖 정보를 추측해 덧붙이지 말 것. 주어진 정보만 근거로 해설.

                ■ 금지 사항
                - "당신은/사용자는" 같은 3인칭 묘사 대신 "학생분의 선택이..." 처럼 자연스러운 존칭 사용.
                - "정답입니다!" 같은 단답 + 이모지 범벅 금지. 설명 내용이 본질.
                - 같은 문장을 반복하거나 선택지 텍스트를 통째로 재복사하는 낭비 금지.
                """;

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        return llmService.call(request).getContent();
    }

    /**
     * 선택지 목록에서 라벨에 해당하는 텍스트를 찾는다.
     */
    private String findOptionText(QuizQuestion question, String label) {
        if (question.getOptions() == null || label == null) {
            return "";
        }
        return question.getOptions().stream()
                .filter(opt -> label.equalsIgnoreCase(opt.getLabel()))
                .map(opt -> opt.getText())
                .findFirst()
                .orElse("");
    }

    /**
     * 오답 시 UserKeyword를 upsert한다.
     * 해당 키워드가 이미 존재하면 weaknessCount를 증가시키고,
     * 존재하지 않으면 새로 생성한다.
     */
    private void upsertWeaknessKeyword(User user, Quiz quiz, String keyword) {
        WeeklyCurriculum curriculum = quiz.getCurriculum();
        LearningRoom room = curriculum.getRoom();

        Optional<UserKeyword> existing = userKeywordRepository
                .findByUserIdAndRoomIdAndKeyword(user.getId(), room.getId(), keyword);

        if (existing.isPresent()) {
            // 기존 약점 키워드의 누적 횟수 증가
            existing.get().incrementWeaknessCount();
        } else {
            // 새 약점 키워드 생성
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

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 학습실 소유권 검증 후 LearningRoom 반환
     */
    private LearningRoom findRoomByOwner(User user, String roomId) {
        return learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
    }

    @lombok.Getter
    @lombok.NoArgsConstructor
    static class LlmRichGradingResponse {
        private Integer score;

        @com.fasterxml.jackson.annotation.JsonProperty("max_score")
        private Integer maxScore;

        private String grade;

        @com.fasterxml.jackson.annotation.JsonProperty("overall_feedback")
        private String overallFeedback;

        @com.fasterxml.jackson.annotation.JsonProperty("keyword_analysis")
        private List<KeywordAnalysis> keywordAnalysis;

        @com.fasterxml.jackson.annotation.JsonProperty("accuracy_score")
        private SubScore accuracyScore;

        @com.fasterxml.jackson.annotation.JsonProperty("depth_score")
        private SubScore depthScore;

        @com.fasterxml.jackson.annotation.JsonProperty("logic_score")
        private SubScore logicScore;

        @com.fasterxml.jackson.annotation.JsonProperty("correct_answer_summary")
        private String correctAnswerSummary;

        @com.fasterxml.jackson.annotation.JsonProperty("improvement_tips")
        private List<String> improvementTips;

        @com.fasterxml.jackson.annotation.JsonProperty("gained_keywords")
        private List<String> gainedKeywords;

        @com.fasterxml.jackson.annotation.JsonProperty("weakness_keywords")
        private List<String> weaknessKeywords;

        @lombok.Getter
        @lombok.NoArgsConstructor
        static class KeywordAnalysis {
            private String keyword;
            private Boolean found;

            @com.fasterxml.jackson.annotation.JsonProperty("in_context")
            private String inContext;

            private String suggestion;

            @com.fasterxml.jackson.annotation.JsonProperty("score_contribution")
            private Integer scoreContribution;
        }

        @lombok.Getter
        @lombok.NoArgsConstructor
        static class SubScore {
            private Integer score;
            private Integer max;
            private String detail;
        }
    }
}
