package com.moai.backend.global.auth;

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
import java.util.Collections;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key; // 암호 도장
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long validityInMilliseconds) {
        // 실제 암호화 알고리즘(HMAC-SHA)에 쓸 수 있는 전용 키 규격으로 변환
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
    }

    // 토큰 생성(문자열)
    public String createToken(String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .subject(email) // 주인 식별자
                .claim("role", role) // 추가 정보
                .issuedAt(now) // 토큰 발급 시간
                .expiration(validity) // 토큰 유효 기간
                .signWith(key) // 암호 도장
                .compact(); // 압축
    }

   // 토큰에서 이메일(Subject) 추출
    public String getEmail(String token) {
        return Jwts.parser() // 암호화된 긴 문자열(토큰)을 다시 우리가 읽을 수 있는 정보(이메일, 권한 등)로 분해하는 도구
                .verifyWith(key) // 유저가 가져온 토큰의 암호 도장이 우리 서버의 암호 도장과 일치하는지 검사
                .build() // 해석기 조립 완료
                .parseSignedClaims(token) // 해석기에 토큰 주입
                .getPayload() // Data 추출
                .getSubject(); // Subject 추출
    }

    // 토큰 유효성 검증 (만료 여부 등)
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false; // 위조되었거나 만료된 경우
        }
    }

    public Authentication getAuthentication(String token) {
        // 토큰 알맹이에서 이메일(Subject)을 꺼내기
        String email = getEmail(token);

        // 스프링 시큐리티가 이해할 수 있는 유저 정보 객체(UserDetails)
        UserDetails userDetails = User.builder()
                .username(email)
                .password("") // 비밀번호는 이미 토큰으로 검증됐으니 필요 없음
                .authorities(Collections.emptyList()) // Null 방지(스터디에 따라 권한이 바뀔 수 있음)
                .build();

        // 3. 표준 신분증인 UsernamePasswordAuthenticationToken을 만들어서 리턴
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

}