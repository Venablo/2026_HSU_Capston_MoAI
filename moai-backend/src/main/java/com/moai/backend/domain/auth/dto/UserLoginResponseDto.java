package com.moai.backend.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserLoginResponseDto {
    private String accessToken;
    private String tokenType = "Bearer"; // Bearer 방식이라고 알려주는 용도

    public UserLoginResponseDto(String accessToken) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
    }
}