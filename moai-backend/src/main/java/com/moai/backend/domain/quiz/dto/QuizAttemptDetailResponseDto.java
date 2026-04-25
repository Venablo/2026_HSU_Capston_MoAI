package com.moai.backend.domain.quiz.dto;

import com.moai.backend.domain.quiz.entity.QuizAttempt;
import com.moai.backend.domain.quiz.entity.QuizOption;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class QuizAttemptDetailResponseDto {

    private String attemptId;
    private String quizTitle;
    private String quizType;
    private String question;
    private String questionType;
    private List<QuizOption> options;
    private String myAnswer;
    private String correctAnswer;
    private Boolean isCorrect;
    private String aiExplanation;
    private String relatedKeyword;
    private LocalDateTime attemptedAt;

    public static QuizAttemptDetailResponseDto from(QuizAttempt attempt) {
        QuizQuestion q = attempt.getQuestion();

        return QuizAttemptDetailResponseDto.builder()
                .attemptId(attempt.getId())
                .quizTitle(attempt.getQuiz().getTitle())
                .quizType(attempt.getQuiz().getQuizType())
                .question(q.getQuestion())
                .questionType(q.getQuestionType())
                .options(q.getOptions())
                .myAnswer(attempt.getSelected())
                .correctAnswer(q.getAnswer())
                .isCorrect(attempt.getIsCorrect())
                .aiExplanation(attempt.getAiExplanation())
                .relatedKeyword(q.getRelatedKeyword())
                .attemptedAt(attempt.getAttemptedAt())
                .build();
    }
}
