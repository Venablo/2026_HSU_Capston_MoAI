package com.moai.backend.domain.flipped.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FlippedStartResponseDto {

    private String sessionId;
    private String firstMessage;
    private List<String> keywords;
    private int currentKeywordIndex;
    private int totalKeywords;
}
