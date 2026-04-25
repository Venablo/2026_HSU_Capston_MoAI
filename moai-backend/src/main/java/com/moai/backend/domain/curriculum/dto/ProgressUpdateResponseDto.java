package com.moai.backend.domain.curriculum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProgressUpdateResponseDto {
    private BigDecimal completionRate;
}
