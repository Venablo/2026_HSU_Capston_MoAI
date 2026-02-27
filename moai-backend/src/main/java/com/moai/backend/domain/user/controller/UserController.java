package com.moai.backend.domain.user.controller;

import com.moai.backend.domain.user.dto.UserSignUpRequestDto;
import com.moai.backend.domain.user.dto.UserSignUpResponseDto;
import com.moai.backend.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    // 회원가입 API
    // POST http://localhost:8080/api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<UserSignUpResponseDto> signUp(@Valid @RequestBody UserSignUpRequestDto requestDto) {

        Long userId = userService.join(requestDto);

        // "201 Created" 응답과 생성된 UserSignUpResponseDto(userId) 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserSignUpResponseDto(userId));
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
