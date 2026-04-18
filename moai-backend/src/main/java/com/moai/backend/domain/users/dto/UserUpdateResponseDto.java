package com.moai.backend.domain.users.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserUpdateResponseDto {
    private String nickname;
    private String profileImageUrl;
    private String themePreference;
}
