package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SseStudyMatchEvent {

    private String type;
    private String message;
    private String suggestionId;
    private PartnerInfo partner;
    private BigDecimal matchScore;
    private String matchKeyword;

    @Getter
    @AllArgsConstructor
    public static class PartnerInfo {
        private String nickname;
        private String role;
    }
}
