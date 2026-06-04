package com.moai.backend.domain.auth.controller;

import com.moai.backend.domain.auth.dto.UserLoginRequestDto;
import com.moai.backend.domain.auth.dto.UserTokenResponseDto;
import com.moai.backend.domain.auth.dto.UserLogoutRequestDto;
import com.moai.backend.domain.auth.dto.UserRefreshRequestDto;
import com.moai.backend.domain.auth.dto.UserRefreshResponseDto;
import com.moai.backend.domain.auth.service.AuthService;
import com.moai.backend.domain.users.dto.UserSignUpRequestDto;
import com.moai.backend.domain.users.dto.UserSignUpResponseDto;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.service.UserService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    // 회원가입 POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserSignUpResponseDto>> register(@Valid @RequestBody UserSignUpRequestDto requestDto) {

        User user = userService.join(requestDto);

        UserSignUpResponseDto responseDto = new UserSignUpResponseDto(
                user.getId(),
                user.getNickname(),
                "환영합니다, " + user.getName() + " 님!"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입 성공", responseDto));
    }

    // 로그인 POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserTokenResponseDto>> login(@Valid @RequestBody UserLoginRequestDto requestDto) {

        UserTokenResponseDto token = authService.login(requestDto);

        return ResponseEntity.ok(ApiResponse.success("로그인 성공", token));
    }

    // 로그아웃 POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String accessToken,
            @RequestBody UserLogoutRequestDto requestDto) {

        String actualToken = accessToken.startsWith("Bearer ")
                ? accessToken.substring(7)
                : accessToken;

        authService.logout(actualToken, requestDto.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("로그아웃되었습니다."));
    }

    // 토큰 갱신 POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserRefreshResponseDto>> refresh(@RequestBody UserRefreshRequestDto requestDto) {

        UserRefreshResponseDto newTokenDto = authService.reissue(requestDto.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", newTokenDto));
    }
}
