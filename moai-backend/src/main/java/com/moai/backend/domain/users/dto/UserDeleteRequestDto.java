package com.moai.backend.domain.users.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserDeleteRequestDto {

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
