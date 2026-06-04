package com.moai.backend.domain.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FinalQuizSubmitResponseDto {

    private String reportId;
    private String status;
    private Short estimatedSec;
}
