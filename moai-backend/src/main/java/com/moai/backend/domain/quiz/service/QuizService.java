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

    public QuizAttemptListResponseDto getQuizAttempts(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        // 해당 주차가 학습실에 속하는지 검증
        weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // quiz → curriculum 조인을 통해 주차별 응시 이력 조회
        List<QuizAttemptListResponseDto.AttemptSummary> attempts =
                quizAttemptRepository.findByUserIdAndQuiz_CurriculumIdOrderByAttemptedAtDesc(
                        user.getId(), weekId
                ).stream()
                        .map(QuizAttemptListResponseDto.AttemptSummary::from)
                        .toList();

        return new QuizAttemptListResponseDto(attempts);
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

        return QuizAttemptResponseDto.builder()
                .attemptId(attempt.getId())
                .isCorrect(isCorrect)
                .correctAnswer(question.getAnswer())
                .aiExplanation(aiExplanation)
                .relatedVideoId(null) // 이벤트 로그의 video_id — 현재 Quiz에서 직접 참조 불가
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
                "[주차 주제] %s\n[키워드] %s\n\n위 주제와 키워드를 기반으로 서술형(essay) 퀴즈 5문제를 생성하세요.",
                curriculum.getTopic(), keywordsStr
        );

        LlmRequestDto llmRequest = LlmRequestDto.builder()
                .systemPrompt("당신은 교육 퀴즈 출제 전문가입니다. "
                        + "주어진 주제와 키워드를 기반으로 서술형 퀴즈 5문제를 JSON으로 생성하세요. "
                        + "각 문제는 키워드 1개 이상과 연관되어야 합니다. "
                        + "tip은 학생이 답변 작성 시 참고할 힌트입니다. "
                        + "maxLength는 답변 최대 글자 수(300~500)입니다. "
                        + "반드시 아래 JSON 형식으로만 응답하세요:\n"
                        + "{\"questions\":[{\"question\":\"문제 본문\",\"relatedKeyword\":\"관련 키워드\","
                        + "\"maxLength\":500,\"tip\":\"힌트 문구\"}]}")
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

        // 비동기 채점 시작
        gradeFinalQuizAsync(report.getId(), quiz.getId(), user.getId(),
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
                questionResult.put("weakKeywords", grading.getWeaknessKeywords());
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
                "[문제]\n%s\n\n[관련 키워드] %s\n\n[학생 답변]\n%s",
                question.getQuestion(),
                question.getRelatedKeyword(),
                studentAnswer
        );

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt("당신은 교육 평가 전문가입니다. "
                        + "서술형 답변을 채점하세요. 20점 만점 기준입니다. "
                        + "gainedKeywords는 학생이 잘 이해한 키워드, weaknessKeywords는 부족한 키워드입니다. "
                        + "반드시 아래 키워드 목록에서만 선택하세요. 목록에 없는 키워드를 생성하지 마세요.\n"
                        + "[키워드 목록] " + keywordsStr + "\n"
                        + "반드시 아래 JSON 형식으로만 응답하세요:\n"
                        + "{\"score\":15,\"gainedKeywords\":[\"키워드1\"],\"weaknessKeywords\":[\"키워드2\"],"
                        + "\"aiComment\":\"해설 문구\"}")
                .userMessage(userMessage)
                .build();

        return llmService.callJson(request, LlmEssayGradingResult.class);
    }

    private String generateRadarData(String gradingSummary, int totalScore) {
        String userMessage = String.format(
                "[총점] %d/100\n\n[문항별 채점 결과]\n%s",
                totalScore, gradingSummary
        );

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt("당신은 학습 분석 전문가입니다. "
                        + "아래 채점 결과를 종합하여 레이더차트용 역량 점수(0~100)를 생성하세요. "
                        + "카테고리: 개념이해도, 응용력, 논리력, 키워드적중률. "
                        + "반드시 아래 JSON 형식으로만 응답하세요:\n"
                        + "{\"개념이해도\":90,\"응용력\":85,\"논리력\":95,\"키워드적중률\":88}")
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

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt("당신은 친절한 교육 튜터입니다. "
                        + "아래 퀴즈 정보를 참고하여 해설을 작성하세요. "
                        + "선택지 라벨(A/B/C/D)은 단순 번호이며 내용과 무관합니다. "
                        + "반드시 '라벨: 선택지 텍스트' 형태로 인용하세요. "
                        + "오답인 경우 학생이 고른 선택지가 왜 틀렸는지, 정답 선택지가 왜 맞는지 설명하세요. "
                        + "2~3문장으로 간결하게 응답하세요.")
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
}
