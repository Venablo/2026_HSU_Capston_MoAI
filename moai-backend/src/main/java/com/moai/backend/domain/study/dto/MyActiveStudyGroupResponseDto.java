package com.moai.backend.domain.study.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyActiveStudyGroupResponseDto {

    private String groupId;
    private String roomId;
    private String curriculumId;
    private String groupStatus;
    private PartnerInfo partner;

    @Getter
    @AllArgsConstructor
    public static class PartnerInfo {
        private String userId;
        private String nickname;
        private String role;
        private String strengthKeyword;
    }
}
