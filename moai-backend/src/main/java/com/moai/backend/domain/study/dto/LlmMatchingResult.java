package com.moai.backend.domain.study.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmMatchingResult {

    private int selectedIndex;
    private BigDecimal matchScore;
    private String matchReason;
}
