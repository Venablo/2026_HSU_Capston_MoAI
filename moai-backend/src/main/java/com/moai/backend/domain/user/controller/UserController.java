package com.moai.backend.domain.user.controller;

import com.moai.backend.domain.user.dto.UserSignUpRequestDto;
import com.moai.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@RequestMapping("/api/users") // 이 컨트롤러의 모든 주소는 /api/users로 시작함
public class UserController {

    private final UserService userService;

    // 회원가입 API
    // POST http://localhost:8080/api/users/signup
    @PostMapping("/signup")
    public ResponseEntity<Long> signUp(@RequestBody UserSignUpRequestDto requestDto) {

        Long userId = userService.join(requestDto);

        // 결과물(ID)과 함께 "200 OK" 신호를 프론트에 보냄
        return ResponseEntity.ok(userId);
    }
}