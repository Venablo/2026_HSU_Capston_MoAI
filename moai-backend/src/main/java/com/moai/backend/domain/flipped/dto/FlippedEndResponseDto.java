package com.moai.backend.domain.flipped.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class FlippedEndResponseDto {

    private String sessionId;
    private String flippedResult;
    private BigDecimal score;
    private List<String> gainedKeywords;
    private List<String> weakKeywords;
    private String feedback;
}
