package com.moai.backend.domain.quiz.dto;

import com.moai.backend.domain.quiz.entity.QuizAttempt;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class QuizAttemptListResponseDto {

    private List<AttemptSummary> attempts;

    @Getter
    @AllArgsConstructor
    public static class AttemptSummary {
        private String attemptId;
        private String quizTitle;
        private String quizType;
        private String questionText;
        private String selected;
        private Boolean isCorrect;
        private LocalDateTime attemptedAt;

        public static AttemptSummary from(QuizAttempt attempt) {
            return new AttemptSummary(
                    attempt.getId(),
                    attempt.getQuiz().getTitle(),
                    attempt.getQuiz().getQuizType(),
                    attempt.getQuestion().getQuestion(),
                    attempt.getSelected(),
                    attempt.getIsCorrect(),
                    attempt.getAttemptedAt()
            );
        }
    }
}
