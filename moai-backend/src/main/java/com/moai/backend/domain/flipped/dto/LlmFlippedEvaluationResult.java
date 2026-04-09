package com.moai.backend.domain.flipped.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmFlippedEvaluationResult {

    private BigDecimal score;
    private String flippedResult;
    private List<String> gainedKeywords;
    private List<String> weakKeywords;
    private String feedback;
}
