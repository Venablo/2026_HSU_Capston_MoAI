package com.moai.backend.domain.auth.service;

import com.moai.backend.domain.auth.dto.UserLoginRequestDto;
import com.moai.backend.domain.auth.dto.UserLogoutRequestDto;
import com.moai.backend.domain.user.entity.User;
import com.moai.backend.domain.user.repository.UserRepository;
import com.moai.backend.global.auth.JwtTokenProvider;
import com.moai.backend.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@Transactional(readOnly = true) // 기본적으로 조회 성능 최적화
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional(readOnly = true)
    public String login(UserLoginRequestDto requestDto) {

        // 1. 이메일 존재 확인
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new CustomException(400, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다."));

        // 2. 비밀번호 일치 확인
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new CustomException(400, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        // 3. 로그인 성공(토큰 발급)
        return jwtTokenProvider.createToken(user.getEmail(), user.getUserRole().getKey());
    }

    @Transactional
    public void logout(UserLogoutRequestDto requestDto) {
        String accessToken = requestDto.getAccessToken();

        // 1. 토큰 유효성 검사 (이미 만료된 토큰이면 굳이 블랙리스트에 넣을 필요 없음)
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new CustomException(401, "AUTH_004", "유효하지 않거나 이미 로그아웃된 토큰입니다.");
        }

        // 2. 토큰의 남은 유효 시간(Expiration) 계산
        Long expiration = jwtTokenProvider.getExpiration(accessToken);

        // 3. Redis에 블랙리스트 등록 (키: 토큰값 / 값: "logout" / 만료시간: 토큰의 남은 시간)
        // 이렇게 하면 토큰의 원래 만료 시간이 지나면 Redis에서도 자동으로 삭제
        redisTemplate.opsForValue()
                .set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);
    }
}
