package com.moai.backend.domain.flipped.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmFlippedEvaluationResult {

    private BigDecimal score;
    private String flippedResult;
    @Setter private List<String> gainedKeywords;
    @Setter private List<String> weakKeywords;
    private String feedback;
}
