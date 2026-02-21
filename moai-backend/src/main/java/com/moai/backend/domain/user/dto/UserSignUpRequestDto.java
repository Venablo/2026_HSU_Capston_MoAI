package com.moai.backend.domain.user.dto;

import com.moai.backend.domain.user.entity.User;
import com.moai.backend.domain.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSignUpRequestDto {

    private String email;
    private String password;
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