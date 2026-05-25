package com.moai.backend.domain.auth.service;

import com.moai.backend.domain.auth.dto.UserLoginRequestDto;
import com.moai.backend.domain.auth.dto.UserRefreshResponseDto;
import com.moai.backend.domain.auth.dto.UserTokenResponseDto;
import com.moai.backend.domain.demo.service.DemoResetService;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.auth.JwtTokenProvider;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final DemoResetService demoResetService;

    @Transactional(readOnly = true)
    public UserTokenResponseDto login(UserLoginRequestDto requestDto) {

        // 1. 로그인 아이디로 사용자 조회
        User user = userRepository.findByLoginId(requestDto.getLoginId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // 2. 비밀번호 일치 확인
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // 3. 탈퇴한 사용자 로그인 차단
        if (!"active".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }

        UserTokenResponseDto tokens = jwtTokenProvider.createToken(user.getEmail(), user.getId(), user.getNickname());

        // 3. Refresh Token을 Redis에 저장
        redisTemplate.opsForValue().set(
                "RT:" + user.getEmail(),
                tokens.getRefreshToken(),
                jwtTokenProvider.getRefreshExpirationTime(),
                TimeUnit.MILLISECONDS
        );

        return tokens;
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // Access Token인지 확인
        if (!"access".equals(jwtTokenProvider.getTokenType(accessToken))) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(accessToken);

        if (redisTemplate.opsForValue().get("RT:" + email) != null) {
            redisTemplate.delete("RT:" + email);
        }

        Long expiration = jwtTokenProvider.getExpiration(accessToken);

        // 토큰 원문 대신 'BL:<sha256>' 키에 저장한다. (JwtTokenProvider.blacklistKey 참조)
        redisTemplate.opsForValue()
                .set(JwtTokenProvider.blacklistKey(accessToken), "logout", expiration, TimeUnit.MILLISECONDS);

        // 시연용 데모 데이터 자동 초기화 — 화이트리스트(application.yaml moai.demo.cleanup-login-ids) 등록 계정만.
        // cleanup 실패는 로그아웃 자체를 막지 않는다 (try/catch).
        try {
            userRepository.findByEmail(email).ifPresent(user -> {
                if (demoResetService.isDemoAccount(user.getLoginId())) {
                    demoResetService.cleanupDemoData(user.getId());
                }
            });
        } catch (Exception e) {
            log.warn("[demo] logout 후 cleanupDemoData 실패 — 로그아웃은 정상 진행: email={}", email, e);
        }
    }

    @Transactional
    public UserRefreshResponseDto reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // Refresh Token인지 확인
        if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(refreshToken);

        String savedToken = redisTemplate.opsForValue().get("RT:" + email);
        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.AUTH_TOKEN_MISMATCH);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // accessToken 만 새로 발급하고 refreshToken 은 그대로 유지한다.
        // 이전에는 createToken 으로 refreshToken 까지 새로 만든 뒤 Redis 만 덮어쓰고
        // 응답에는 새 RT 를 싣지 않아, 다음 refresh 때 클라이언트의 옛 RT 와 Redis 값이
        // 불일치하여 AUTH_TOKEN_MISMATCH 가 발생했다.
        JwtTokenProvider.AccessTokenInfo accessTokenInfo =
                jwtTokenProvider.createAccessToken(user.getEmail(), user.getId());

        return new UserRefreshResponseDto(accessTokenInfo.token(), accessTokenInfo.expiresIn());
    }
}
