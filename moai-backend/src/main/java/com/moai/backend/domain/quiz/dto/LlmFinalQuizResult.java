package com.moai.backend.domain.quiz.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class LlmFinalQuizResult {

    private List<QuestionData> questions;

    @Getter
    @NoArgsConstructor
    public static class QuestionData {
        private String question;
        private String relatedKeyword;
        private Short maxLength;
        private String tip;
    }
}
