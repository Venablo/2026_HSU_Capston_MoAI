package com.moai.backend.domain.user.controller;

import com.moai.backend.domain.user.dto.LoginResponseDto;
import com.moai.backend.domain.user.dto.UserLoginRequestDto;
import com.moai.backend.domain.user.dto.UserSignUpRequestDto;
import com.moai.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    // 회원가입 API
    // POST http://localhost:8080/api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody UserSignUpRequestDto requestDto) {

        Long userId = userService.join(requestDto);

        // 결과물(ID)과 함께 "200 OK" 신호를 프론트에 보냄
        return ResponseEntity.ok(userId);
    }

    // 로그인 API
    // POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody UserLoginRequestDto requestDto) {

        String token = userService.login(requestDto);

        // LoginResponseDto(토큰)와 함께 "200 OK" 신호를 프론트에 보냄
        return ResponseEntity.ok(new LoginResponseDto(token));
    }

    // Test
    @GetMapping("/me")
    public ResponseEntity<String> testMe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            // 보관함에 명찰이 있다면 이메일이 출력
            return ResponseEntity.ok("현재 접속한 유저: " + userDetails.getUsername());
        }
        return ResponseEntity.status(401).body("로그인이 필요합니다.");
    }
}
