package com.moai.backend.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class FinalQuizSubmitRequestDto {

    @NotBlank
    private String quizId;

    @NotEmpty
    private List<AnswerItem> answers;

    @Getter
    @NoArgsConstructor
    public static class AnswerItem {
        @NotBlank
        private String questionId;
        @NotBlank
        private String answer;
    }
}
