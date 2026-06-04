package com.moai.backend.domain.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QuizAttemptRequestDto {

    @NotBlank
    private String questionId;

    @NotBlank
    private String quizId;

    @NotBlank
    private String selected;
}
