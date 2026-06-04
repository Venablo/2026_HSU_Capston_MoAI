package com.moai.backend.domain.quiz.dto;

import com.moai.backend.domain.quiz.entity.QuizQuestion;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FinalQuizResponseDto {

    private String quizId;
    private String title;
    private List<QuestionItem> questions;

    @Getter
    @AllArgsConstructor
    public static class QuestionItem {
        private String questionId;
        private String questionType;
        private Short order;
        private String question;
        private String relatedKeyword;
        private Short maxLength;
        private String tip;

        public static QuestionItem from(QuizQuestion q) {
            return new QuestionItem(
                    q.getId(),
                    q.getQuestionType(),
                    q.getQuestionOrder(),
                    q.getQuestion(),
                    q.getRelatedKeyword(),
                    q.getMaxLength(),
                    q.getTip()
            );
        }
    }
}
