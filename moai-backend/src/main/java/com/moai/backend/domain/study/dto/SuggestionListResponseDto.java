package com.moai.backend.domain.study.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SuggestionListResponseDto {

    private String suggestionId;
    private String suggestedRole;
    private PartnerInfo partner;
    private BigDecimal matchScore;
    private String matchKeyword;
    private String matchReason;
    private String status;

    @Getter
    @AllArgsConstructor
    public static class PartnerInfo {
        private String nickname;
        private String profileImageUrl;
        private String strengthKeyword;
    }
}
