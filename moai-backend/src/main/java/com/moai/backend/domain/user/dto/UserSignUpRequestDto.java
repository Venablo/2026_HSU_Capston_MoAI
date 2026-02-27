package com.moai.backend.domain.user.dto;

import com.moai.backend.domain.user.entity.User;
import com.moai.backend.domain.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSignUpRequestDto {

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    private String nickname;

    // 회원 가입을 위한 DTO
    public User toEntity(String encodedPassword) {
        return User.builder()
                .email(email)
                .password(encodedPassword) // 암호화된 비밀번호 주입
                .nickname(nickname)
                .userRole(UserRole.STUDENT) // 회원가입 시 기본 권한 설정
                .build();
    }
}