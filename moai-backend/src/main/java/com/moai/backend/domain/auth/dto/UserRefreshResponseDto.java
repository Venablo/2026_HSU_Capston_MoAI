package com.moai.backend.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserRefreshResponseDto {
    private String accessToken;
    private long expiresIn;
}
