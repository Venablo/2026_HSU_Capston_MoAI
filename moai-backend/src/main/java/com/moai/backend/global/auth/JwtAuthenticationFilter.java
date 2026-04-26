package com.moai.backend.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
public class JwtAuthenticationFilter extends OncePerRequestFilter { // 한 요청당 한번만 실행되는 필터

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청 헤더에서 토큰 꺼내기
        String token = resolveToken(request);

        // 토큰 존재 여부, 유효성 검사
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // Access Token만 API 인증에 사용 (Refresh Token으로 API 접근 차단)
            String tokenType = jwtTokenProvider.getTokenType(token);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Redis에서 해당 토큰이 로그아웃된 상태인지 확인
            String isLogout = redisTemplate.opsForValue().get(token);

            if (isLogout == null) { // Redis에 없을 때만(정상 토큰일 때만) 인증 정보를 세션에 저장
                // 토큰에서 유저 정보(명찰)를 꺼내옴
                Authentication authentication = jwtTokenProvider.getAuthentication(token);

                // 스프링 시큐리티의 세션 주머니(Context)에 이 명찰을 넣어줌
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 순수 토큰만 가져오는 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) { // 문자열 검사 및 'Bearer ' 시작
            return bearerToken.substring(7); // 순수 토큰 추출
        }
        // SSE(EventSource) 처럼 헤더 설정이 불가한 경우 query param 폴백
        String queryToken = request.getParameter("access_token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        // 프론트엔드 EventSource가 ?token= 형태로 전달하는 경우
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}
