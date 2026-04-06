package com.moai.backend.domain.quiz.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.quiz.dto.InstantQuizResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptRequestDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptResponseDto;
import com.moai.backend.domain.quiz.entity.Quiz;
import com.moai.backend.domain.quiz.entity.QuizAttempt;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final LlmService llmService;

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
