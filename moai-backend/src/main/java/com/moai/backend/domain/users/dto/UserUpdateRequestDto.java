package com.moai.backend.domain.users.dto;

import lombok.Getter;

@Getter
public class UserUpdateRequestDto {
    private String nickname;
    private String profileImageUrl;
    private String themePreference;
}
