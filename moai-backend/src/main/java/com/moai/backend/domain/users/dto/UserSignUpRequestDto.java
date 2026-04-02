package com.moai.backend.domain.users.dto;

import com.moai.backend.domain.users.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSignUpRequestDto {

    @NotBlank(message = "로그인 아이디는 필수 입력 항목입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password;

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    private String nickname;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    private LocalDate birthDate;

    private List<String> interestKeywords;

    public User toEntity(String encodedPassword) {
        return User.builder()
                .loginId(loginId)
                .passwordHash(encodedPassword)
                .name(name)
                .nickname(nickname)
                .email(email)
                .birthDate(birthDate)
                .interestKeywords(interestKeywords)
                .build();
    }
}
