package com.moai.backend.domain.study.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StudyGroupDetailResponseDto {

    private String groupId;
    private String matchKeyword;
    private String status;
    private PartnerDetail partner;

    @Getter
    @AllArgsConstructor
    public static class PartnerDetail {
        private String userId;
        private String nickname;
        private String profileImageUrl;
        private String role;
        private boolean isOnline;
    }
}
