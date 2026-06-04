package com.moai.backend.domain.users.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpResponseDto {
    private String userId;
    private String nickname;
    private String message;
}
