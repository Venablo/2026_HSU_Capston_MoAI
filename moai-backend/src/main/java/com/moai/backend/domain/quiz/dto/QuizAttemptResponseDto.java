package com.moai.backend.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizAttemptResponseDto {

    private String attemptId;
    private boolean isCorrect;
    private String correctAnswer;
    private String aiExplanation;
    private String relatedVideoId;
    private Integer relatedTimestamp;
    private Integer rewindToSec;
}
