package com.moai.backend.domain.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class QuizReportResponseDto {

    private String status;
    private Short estimatedSec;
    private Object finalScore;
    private Object radarData;
    private Object questions;
}
