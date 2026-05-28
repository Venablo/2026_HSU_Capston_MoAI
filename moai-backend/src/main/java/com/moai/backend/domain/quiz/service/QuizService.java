package com.moai.backend.domain.quiz.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.keyword.util.KeywordNormalizer;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.moai.backend.domain.quiz.entity.QuizOption;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.entity.QuizReport;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizReportRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.notification.dto.SseSimpleEvent;
import com.moai.backend.domain.notification.service.NotificationService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CurriculumEnrichmentService curriculumEnrichmentService;
    private final NotificationService notificationService;

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
     * 4) relatedKeyword 기반 UserKeyword 갱신
     *    - 정답: 강점 승격 + 기존 약점 해소 (파이널 퀴즈/거꾸로 학습과 동일)
     *    - 오답: 약점 누적 upsert
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

        // 정답 비교 전 원본 값 로깅 (DB 정답 / 클라이언트 선택값)
        log.info("[QuizSubmit] questionId={} dbAnswer='{}' selected='{}'",
                question.getId(), question.getAnswer(), request.getSelected());

        // DB 정답은 라벨, 프론트 selected 는 라벨 또는 선택지 텍스트일 수 있으므로
        // 양쪽 모두 라벨로 정규화한 뒤 비교한다.
        List<QuizOption> options = question.getOptions();
        String correctLabel = normalizeToLabel(question.getAnswer(), options);
        String selectedLabel = normalizeToLabel(request.getSelected(), options);

        boolean isCorrect = correctLabel != null
                && correctLabel.equalsIgnoreCase(selectedLabel);

        log.info("[QuizSubmit] correctLabel='{}' selectedLabel='{}' match={}",
                correctLabel, selectedLabel, isCorrect);

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

        Integer rewindToSec = null;
        // 4. relatedKeyword 기반 UserKeyword 갱신
        if (question.getRelatedKeyword() != null) {
            if (isCorrect) {
                // 정답: 파이널 퀴즈/거꾸로 학습과 동일하게 강점 승격(약점 보유 시 해소)
                WeeklyCurriculum curriculum = quiz.getCurriculum();
                LearningRoom room = curriculum.getRoom();
                resolveAndPromoteKeywords(user, room, curriculum,
                        List.of(question.getRelatedKeyword()));
            } else {
                upsertWeaknessKeyword(user, quiz, question.getRelatedKeyword());

                // 5. 오답 시 Quiz.rewindToSec 조회하여 되감기 지점 반환
                rewindToSec = quiz.getRewindToSec();
            }
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
        List<String> keywords = curriculumKeywordsOrTopic(curriculum);
        LearningRoom room = curriculum.getRoom();
        String subject = (room != null) ? room.getSubject() : curriculum.getTopic();
        String level = (room != null) ? room.getLevel() : "";

        String userMessage = String.format(
                "{\"subject\":%s,\"curriculum_topic\":%s,\"week_number\":%d,\"key_concepts\":[%s]}",
                quoteJson(subject), quoteJson(curriculum.getTopic()), (int) curriculum.getWeekNumber(),
                keywords.stream().map(this::quoteJson).collect(java.util.stream.Collectors.joining(","))
        );

        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 주차 마무리 퀴즈 출제 AI입니다.

                학습 주제: %s (수준: %s)
                반드시 '%s' 학습 맥락에서 문제를 출제하세요. 다른 과목이나 분야의 내용으로 출제하지 마세요.

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
                """, subject, level, subject);

        LlmRequestDto llmRequest = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        LlmFinalQuizResult result = llmService.callJson(llmRequest, LlmFinalQuizResult.class);
        if (result == null || result.getQuestions() == null || result.getQuestions().isEmpty()) {
            throw new IllegalStateException("파이널 퀴즈 생성 응답이 비어있습니다");
        }

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

    private List<String> curriculumKeywordsOrTopic(WeeklyCurriculum curriculum) {
        if (curriculum.getKeywords() != null && !curriculum.getKeywords().isEmpty()) {
            return curriculum.getKeywords();
        }

        String topic = curriculum.getTopic();
        if (topic != null && !topic.isBlank()) {
            return List.of(topic);
        }

        return List.of("핵심 개념");
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

        // 중복 제출 검사 — failed 상태는 삭제 후 재시도 허용
        quizReportRepository.findByUserIdAndCurriculumId(user.getId(), weekId).ifPresent(existing -> {
            if ("failed".equals(existing.getStatus())) {
                quizReportRepository.delete(existing);
            } else {
                throw new CustomException(ErrorCode.FINAL_QUIZ_ALREADY_SUBMITTED);
            }
        });

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

        // 트랜잭션 커밋 완료 후 비동기 채점 시작 (커밋 전 조회 오류 방지)
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        self.gradeFinalQuizAsync(report.getId(), quiz.getId(), user.getId(),
                                room.getId(), curriculum.getId(), request.getAnswers());
                    }
                }
        );

        return new FinalQuizSubmitResponseDto(report.getId(), "analyzing", (short) 15);
    }

    @Async
    @Transactional
    public void gradeFinalQuizAsync(String reportId, String quizId, String userId,
                                     String roomId, String curriculumId,
                                     List<FinalQuizSubmitRequestDto.AnswerItem> answers) {
        log.info("비동기 채점 시작: reportId={}", reportId);
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
            List<String> curriculumKeywords = curriculumKeywordsOrTopic(curriculum);
            // radarData 생성용 요약 수집
            StringBuilder gradingSummary = new StringBuilder();

            for (FinalQuizSubmitRequestDto.AnswerItem answerItem : answers) {
                QuizQuestion question = quizQuestionRepository.findById(answerItem.getQuestionId())
                        .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_QUESTION_NOT_FOUND));

                // LLM 서술형 채점 (커리큘럼 키워드 제약)
                LlmEssayGradingResult grading = gradeEssayQuestion(
                        question, answerItem.getAnswer(), curriculumKeywords, room);

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

            // 주차 완료(100%) 시 다음 주차 unlock 및 약점 키워드 보충 트리거
            advanceWeekIfCompleted(curriculum, room, userId, curriculumId);

        } catch (JsonProcessingException e) {
            log.error("파이널 퀴즈 리포트 JSON 직렬화 실패", e);
            markReportFailed(reportId);
        } catch (Exception e) {
            log.error("파이널 퀴즈 비동기 채점 실패: reportId={}", reportId, e);
            markReportFailed(reportId);
        }
    }

    private void markReportFailed(String reportId) {
        try {
            quizReportRepository.findById(reportId).ifPresent(r -> {
                r.fail();
                quizReportRepository.save(r);
            });
        } catch (Exception ex) {
            log.error("채점 실패 상태 저장 실패: reportId={}", reportId, ex);
        }
    }

    private LlmEssayGradingResult gradeEssayQuestion(QuizQuestion question, String studentAnswer,
                                                      List<String> curriculumKeywords, LearningRoom room) {
        String keywordsStr = String.join(", ", curriculumKeywords);
        String subject = (room != null) ? room.getSubject() : "";
        String level = (room != null) ? room.getLevel() : "";

        String userMessage = String.format(
                "{\"subject\":%s,\"question\":%s,\"related_keyword\":%s,\"max_score\":20,\"student_answer\":%s,\"curriculum_keywords\":[%s]}",
                quoteJson(subject),
                quoteJson(question.getQuestion()),
                quoteJson(question.getRelatedKeyword()),
                quoteJson(studentAnswer),
                curriculumKeywords.stream().map(this::quoteJson).collect(java.util.stream.Collectors.joining(","))
        );

        String systemPrompt = String.format("""
                당신은 MoAI 학습 플랫폼의 AI 채점 전문가입니다.

                학습 주제: %s (수준: %s)
                반드시 '%s' 학습 맥락에서 채점하세요.

                학습자의 서술형 답변을 분석하여 간결한 채점 결과와 피드백을 제공하세요.

                ■ 출력: 순수 JSON (코드블록 없이)
                {
                  "score": 0~20,
                  "max_score": 20,
                  "grade": "A+/A/B+/B/C+/C/D/F",
                  "overall_feedback": "종합 피드백 1~2문장. 가장 핵심적인 강점 또는 부족한 점 하나만. 반복·장황 금지.",
                  "keyword_analysis": [
                    {"keyword":"필수키워드","found":true,"in_context":"문맥(10자 이내)","score_contribution":4},
                    {"keyword":"빠진키워드","found":false,"suggestion":"보완 방법(10자 이내)","score_contribution":0}
                  ],
                  "accuracy_score": {"score":0,"max":8,"detail":"10자 이내 한 줄"},
                  "depth_score": {"score":0,"max":6,"detail":"10자 이내 한 줄"},
                  "logic_score": {"score":0,"max":6,"detail":"10자 이내 한 줄"},
                  "correct_answer_summary": "핵심 키워드 2~3개 나열 수준의 1문장. 설명 없이 키워드만.",
                  "improvement_tips": ["개선 팁 1문장"],
                  "gained_keywords": ["학생이 잘 이해한 키워드"],
                  "weakness_keywords": ["학생이 부족한 키워드"]
                }

                ■ 필수 규칙:
                1. scoring_rubric이 있다면 그 기준에 따라 엄격하되 공정하게 채점
                2. keyword_analysis에서 각 필수 키워드의 등장 여부와 맥락 분석
                3. 부분 점수 인정 (키워드는 있지만 설명이 부정확한 경우 등)
                4. 모든 텍스트 필드: 핵심만, 장황 금지. detail·summary는 각 10~15자 이내 초간결하게.
                5. gained_keywords, weakness_keywords 는 반드시 입력된 curriculum_keywords 목록에서만 선택. 목록 외 임의 생성 금지.
                   [허용 키워드] %s
                6. 문항별 해설은 반드시 3줄 화면 형식(점수 / 핵심 / 보완)에 맞춘다.
                   최종 해설은 줄바꿈으로 줄을 나눈다.
                   각 피드백 필드는 한 문장만 작성하고 문단형 설명은 금지한다.
                """, subject, level, subject, keywordsStr);

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

        int score = raw.getScore() != null ? raw.getScore() : 0;
        List<String> gained = raw.getGainedKeywords() != null ? raw.getGainedKeywords() : List.of();
        List<String> weak = raw.getWeaknessKeywords() != null ? raw.getWeaknessKeywords() : List.of();

        List<String> lines = new ArrayList<>();
        String feedback = compactLine(raw.getOverallFeedback(), 70);
        lines.add(feedback.isBlank()
                ? String.format("🎯 점수: %d/20", score)
                : String.format("🎯 점수: %d/20. %s", score, feedback));

        String summary = compactLine(raw.getCorrectAnswerSummary(), 80);
        if (!summary.isBlank()) {
            lines.add("🧠 핵심: " + summary);
        }

        String tip = "";
        if (raw.getImprovementTips() != null && !raw.getImprovementTips().isEmpty()) {
            tip = compactLine(raw.getImprovementTips().get(0), 70);
        }
        if (tip.isBlank() && !weak.isEmpty()) {
            tip = compactLine(String.join(", ", weak), 70);
        }
        if (!tip.isBlank()) {
            lines.add("💡 보완: " + tip);
        }

        return new LlmEssayGradingResult(score, gained, weak, String.join("\n\n", lines));
    }

    private String compactLine(String value, int maxLength) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

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
                  "적용력": 0~100,
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

        try {
            String raw = llmService.call(request).getContent().trim();
            return normalizeRadarDataJson(raw, totalScore);
        } catch (Exception e) {
            log.warn("Radar data generation failed. Using fallback. totalScore={}", totalScore, e);
            return fallbackRadarDataJson(totalScore);
        }
    }

    private String normalizeRadarDataJson(String raw, int totalScore) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(extractJsonObject(raw));
        Map<String, Integer> radar = new LinkedHashMap<>();
        radar.put("개념이해도", clampRadarValue(node.path("개념이해도").asInt(totalScore)));
        radar.put("적용력", clampRadarValue(node.path("적용력").asInt(totalScore)));
        radar.put("논리력", clampRadarValue(node.path("논리력").asInt(totalScore)));
        radar.put("키워드적중률", clampRadarValue(node.path("키워드적중률").asInt(totalScore)));
        return objectMapper.writeValueAsString(radar);
    }

    private String extractJsonObject(String text) {
        int start = text == null ? -1 : text.indexOf('{');
        int end = text == null ? -1 : text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text == null ? "{}" : text;
    }

    private int clampRadarValue(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String fallbackRadarDataJson(int totalScore) {
        Map<String, Integer> radar = new LinkedHashMap<>();
        int score = clampRadarValue(totalScore);
        radar.put("개념이해도", score);
        radar.put("적용력", score);
        radar.put("논리력", score);
        radar.put("키워드적중률", score);

        try {
            return objectMapper.writeValueAsString(radar);
        } catch (JsonProcessingException e) {
            return "{\"개념이해도\":0,\"적용력\":0,\"논리력\":0,\"키워드적중률\":0}";
        }
    }

    private void resolveAndPromoteKeywords(User user, LearningRoom room,
                                            WeeklyCurriculum curriculum,
                                            List<String> gainedKeywords) {
        if (gainedKeywords == null || gainedKeywords.isEmpty()) return;
        // 커리큘럼 핵심 키워드 원형으로 정규화 (LLM 이 영문/변형 표기로 반환해도 통일)
        gainedKeywords = KeywordNormalizer.normalize(gainedKeywords, curriculum.getKeywords());
        if (gainedKeywords.isEmpty()) return;

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
        // 커리큘럼 핵심 키워드 원형으로 정규화 (LLM 이 영문/변형 표기로 반환해도 통일)
        weaknessKeywords = KeywordNormalizer.normalize(weaknessKeywords, curriculum.getKeywords());
        if (weaknessKeywords.isEmpty()) return;

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

    /**
     * 파이널 퀴즈 완료로 주차가 100%가 됐을 때 다음 주차를 unlock하고,
     * 미해소 약점 키워드가 있으면 다음 주차에 보충 콘텐츠를 비동기 생성한다.
     */
    private void advanceWeekIfCompleted(WeeklyCurriculum curriculum, LearningRoom room,
                                         String userId, String curriculumId) {
        if (curriculum.getCompletionRate().compareTo(MAX_COMPLETION) < 0) return;
        if (curriculum.getWeekNumber() >= room.getDurationWeeks()) return;

        short nextWeekNumber = (short) (curriculum.getWeekNumber() + 1);
        room.updateCurrentWeek(nextWeekNumber);

        // 트랜잭션 커밋 후 SSE 전송: 프론트엔드가 새로고침 없이 다음 주차를 바로 열 수 있게 함
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            notificationService.pushSse(userId,
                                    new SseSimpleEvent("week_unlocked", String.valueOf(nextWeekNumber)));
                        } catch (Exception e) {
                            log.warn("week_unlocked SSE 전송 실패: userId={}, week={}", userId, nextWeekNumber, e);
                        }
                    }
                }
        );

        List<UserKeyword> unresolvedWeaknesses =
                userKeywordRepository
                        .findByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqualOrderByWeaknessCountDesc(
                                userId, curriculumId, "weakness", (short) 1);

        if (unresolvedWeaknesses.isEmpty()) return;

        List<String> keywordNames = unresolvedWeaknesses.stream()
                .map(UserKeyword::getKeyword)
                .distinct()
                .toList();

        final short currentWeekNumber = curriculum.getWeekNumber();
        weeklyCurriculumRepository.findByRoomIdAndWeekNumber(room.getId(), nextWeekNumber)
                .ifPresent(nextCurriculum ->
                        curriculumEnrichmentService.enrichWithWeaknessKeywords(
                                nextCurriculum.getId(), keywordNames,
                                room.getSubject(), room.getLevel(), currentWeekNumber
                        )
                );
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

        if ("failed".equals(report.getStatus())) {
            return QuizReportResponseDto.builder()
                    .status("failed")
                    .build();
        }

        Object radarData = readRadarData(report);
        Object questions = readQuestions(report);

        return QuizReportResponseDto.builder()
                .status(report.getStatus())
                .finalScore(report.getFinalScore())
                .radarData(radarData)
                .questions(questions)
                .build();
    }

    private Object readRadarData(QuizReport report) {
        try {
            if (report.getRadarData() != null && !report.getRadarData().isBlank()) {
                return objectMapper.readValue(report.getRadarData(), Object.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("QuizReport radarData parse failed. Using fallback. reportId={}", report.getId(), e);
        }

        try {
            return objectMapper.readValue(fallbackRadarDataJson(reportScoreAsInt(report)), Object.class);
        } catch (JsonProcessingException e) {
            Map<String, Integer> fallback = new LinkedHashMap<>();
            fallback.put("개념이해도", 0);
            fallback.put("적용력", 0);
            fallback.put("논리력", 0);
            fallback.put("키워드적중률", 0);
            return fallback;
        }
    }

    private Object readQuestions(QuizReport report) {
        try {
            if (report.getQuestions() != null && !report.getQuestions().isBlank()) {
                return objectMapper.readValue(report.getQuestions(), Object.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("QuizReport questions parse failed. Returning empty questions. reportId={}", report.getId(), e);
        }
        return List.of();
    }

    private int reportScoreAsInt(QuizReport report) {
        if (report.getFinalScore() == null) {
            return 0;
        }
        return clampRadarValue(report.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue());
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

                역할: 학생이 방금 응답한 문항에 대해 핵심만 짚는 읽기 쉬운 해설을 제공합니다.

                ■ 출력 형식: 한국어 존댓말. 흐르는 문장과 불릿(-)을 자유롭게 혼합해 구성. 여러 항목을 나열할 때는 불릿과 줄바꿈을 활용하고, 자연스럽게 이어지는 내용은 문장으로 작성해도 됨. 하나의 긴 문단 덩어리는 피할 것.
                반드시 문단(텍스트 블록)과 불릿 목록 사이에는 빈 줄을 넣을 것 — 마크다운 렌더러에서 줄바꿈이 올바르게 표시됨.
                예시 구조 (고정 형식 아님, 상황에 맞게 변형 가능):
                  정답/오답 여부와 핵심 이유를 1~2문장으로 자연스럽게 설명.

                  - 핵심 개념: 관련 키워드 의미나 올바른 내용
                  - 오답 분석: 왜 틀렸는지 (오답일 때만)

                  격려 한 줄.

                ■ 필수 규칙
                1. 선택지 인용 시 "A: 선택지 텍스트" 형태로 라벨과 원문 함께 인용.
                2. 관련 키워드를 "한글(영문)" 형태로 1회만 병기.
                3. 문항 밖 정보 추측 금지. 주어진 정보만 근거로.
                4. 핵심만 — 부연 설명, 반복, 장황한 문장 모두 금지. 읽는 데 10초면 충분한 분량.

                ■ 금지 사항
                - 줄 바꿈 없이 이어지는 긴 문단 금지.
                - 이모지 남발 금지.
                - 같은 내용 반복 금지.
                - 이미 선택지에 나온 내용을 그대로 다시 쓰는 것 금지.
                """;

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        return llmService.call(request).getContent();
    }

    /**
     * 라벨 또는 선택지 텍스트 어느 쪽이 들어와도 라벨로 정규화한다.
     * - 한 글자 알파벳이면 그대로 대문자 라벨로 간주
     * - 그 외에는 options 에서 text 가 일치하는 항목의 label 반환
     * - 어느 쪽에도 해당 안 되면 원본 trim 값 반환 (→ 자연스럽게 false 판정)
     */
    private String normalizeToLabel(String value, List<QuizOption> options) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;

        // 한 글자 알파벳 → 그대로 대문자 라벨로 처리
        if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
            return trimmed.toUpperCase();
        }

        if (options == null) return trimmed;

        // 옵션 텍스트와 매칭되는 label 찾기 (대소문자 무시, 공백 정규화)
        return options.stream()
                .filter(o -> o.getText() != null
                        && o.getText().trim().equalsIgnoreCase(trimmed))
                .map(QuizOption::getLabel)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .findFirst()
                .orElse(trimmed);
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
