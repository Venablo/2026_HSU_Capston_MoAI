package com.moai.backend.domain.flipped.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class FlippedResultResponseDto {

    private BigDecimal score;
    private List<String> gainedKeywords;
    private List<String> weakKeywords;
    private String feedback;
    private List<ConversationItem> conversations;

    @Getter
    @AllArgsConstructor
    public static class ConversationItem {
        private String role;
        private String content;
    }
}
