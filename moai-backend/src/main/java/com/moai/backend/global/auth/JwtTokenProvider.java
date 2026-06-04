package com.moai.backend.global.auth;

import com.moai.backend.domain.auth.dto.UserTokenResponseDto;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public UserTokenResponseDto createToken(String email, String userId, String nickname) {
        Date now = new Date();

        String accessToken = Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();

        String refreshToken = Jwts.builder()
                .subject(email)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();

        return new UserTokenResponseDto(accessToken, refreshToken, accessTokenExpiration / 1000, userId, nickname);
    }

    // 토큰 재발급 전용: refreshToken 은 재생성하지 않고 accessToken 만 발급한다.
    // 호출자(AuthService.reissue)는 Redis 의 기존 RT 를 그대로 두어야 한다.
    public AccessTokenInfo createAccessToken(String email, String userId) {
        Date now = new Date();
        String accessToken = Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
        return new AccessTokenInfo(accessToken, accessTokenExpiration / 1000);
    }

    public record AccessTokenInfo(String token, long expiresIn) {}

    // 로그아웃된 accessToken 을 Redis 블랙리스트에 저장/조회할 때 사용하는 키.
    // 토큰 원문을 키로 쓰면 Redis MONITOR / 백업 파일 / 슬로우로그 등에서 토큰이
    // 그대로 노출되어 세션 탈취 위험이 있고 키 사이즈도 비효율적이라,
    // 'BL:' 네임스페이스 prefix + SHA-256 hex 해시로 변환한다.
    public static String blacklistKey(String accessToken) {
        return "BL:" + sha256Hex(accessToken);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    public String getEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String getUserId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", String.class);
    }

    // 토큰에서 type 클레임 추출 ("access" 또는 "refresh")
    public String getTokenType(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("type", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰 상태 — 필터에서 만료/무효를 구분하기 위해 사용
    public enum TokenStatus { VALID, EXPIRED, INVALID }

    // validateToken 과 달리 만료(ExpiredJwtException) 와 그 외 무효를 구분해서 반환한다.
    // 필터에서 클라이언트에게 정확한 에러 코드를 내려보내기 위해 필요하다.
    public TokenStatus parseTokenStatus(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return TokenStatus.VALID;
        } catch (ExpiredJwtException e) {
            return TokenStatus.EXPIRED;
        } catch (Exception e) {
            return TokenStatus.INVALID;
        }
    }

    public Authentication getAuthentication(String token) {
        String email = getEmail(token);

        UserDetails userDetails = User.builder()
                .username(email)
                .password("")
                .authorities(Collections.emptyList())
                .build();

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public long getRefreshExpirationTime() {
        return refreshTokenExpiration;
    }

    public Long getExpiration(String accessToken) {
        Date expiration = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload()
                .getExpiration();

        long now = new Date().getTime();
        return (expiration.getTime() - now);
    }
}
