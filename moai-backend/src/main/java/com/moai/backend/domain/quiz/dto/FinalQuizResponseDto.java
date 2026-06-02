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
        // 시연용 미리 채워둔 답안 (mock 시드 분기에서만 값, 그 외엔 null)
        private String prefilledAnswer;

        public static QuestionItem from(QuizQuestion q) {
            return from(q, null);
        }

        public static QuestionItem from(QuizQuestion q, String prefilledAnswer) {
            return new QuestionItem(
                    q.getId(),
                    q.getQuestionType(),
                    q.getQuestionOrder(),
                    q.getQuestion(),
                    q.getRelatedKeyword(),
                    q.getMaxLength(),
                    q.getTip(),
                    prefilledAnswer
            );
        }
    }
}
