package com.moai.backend.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserTokenResponseDto {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String userId;
    private String nickname;
}
