package com.moai.backend.domain.auth.controller;

import com.moai.backend.domain.auth.dto.UserLoginRequestDto;
import com.moai.backend.domain.auth.dto.UserLoginResponseDto;
import com.moai.backend.domain.auth.dto.UserLogoutRequestDto;
import com.moai.backend.domain.auth.dto.UserLogoutResponseDto;
import com.moai.backend.domain.auth.service.AuthService;
import com.moai.backend.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RedisTemplate<String, String> redisTemplate;

    // 로그인 API
    // POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponseDto> login(@Valid @RequestBody UserLoginRequestDto requestDto) {

        String token = authService.login(requestDto);

        // LoginResponseDto(토큰)와 함께 "200 OK" 응답 반환
        return ResponseEntity.ok(new UserLoginResponseDto(token));
    }

    // 로그아웃 API
    // POST http://localhost:8080/api/auth/logout

    @PostMapping("/logout")
    public ResponseEntity<UserLogoutResponseDto> logout(@RequestBody UserLogoutRequestDto requestDto) {

        authService.logout(requestDto);

        return ResponseEntity.ok(new UserLogoutResponseDto("로그아웃 되었습니다."));
    }

    // [개발 확인용] Redis에 블랙리스트 토큰이 잘 들어갔는지 조회

    @GetMapping("/debug/blacklist")
    public ResponseEntity<String> checkBlacklist(@RequestParam String token) {
        // Redis에서 해당 토큰(Key)으로 저장된 값이 있는지 확인
        String status = redisTemplate.opsForValue().get(token);

        if (status != null) {
            return ResponseEntity.ok("상태: [블랙리스트] - " + status + " 처리된 토큰입니다.");
        } else {
            return ResponseEntity.ok("상태: [정상] - 현재 블랙리스트에 존재하지 않는 토큰입니다.");
        }
    }
}
