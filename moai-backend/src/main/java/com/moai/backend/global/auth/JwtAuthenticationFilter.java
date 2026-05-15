package com.moai.backend.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
public class JwtAuthenticationFilter extends OncePerRequestFilter { // 한 요청당 한번만 실행되는 필터

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청 헤더에서 토큰 꺼내기
        String token = resolveToken(request);

        // 토큰이 아예 없으면 anonymous 로 통과 — 공개 엔드포인트는 SecurityConfig 의 permitAll 로 처리되고,
        // 보호 엔드포인트는 Spring Security 가 403 으로 거부한다.
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰이 있다면 만료/무효를 구분하여 즉시 401 + 명확한 errorCode 로 응답한다.
        // (기존에는 invalid 토큰이 들어와도 그냥 통과시켜 Spring Security 가 403 을 던졌고,
        //  프론트가 만료를 인식하지 못해 자동 갱신을 건너뛰는 문제가 있었음)
        JwtTokenProvider.TokenStatus status = jwtTokenProvider.parseTokenStatus(token);
        if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
            writeUnauthorized(response, ErrorCode.AUTH_TOKEN_EXPIRED);
            return;
        }
        if (status == JwtTokenProvider.TokenStatus.INVALID) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // Access Token 만 API 인증에 사용 (Refresh Token 으로 API 접근 차단)
        String tokenType = jwtTokenProvider.getTokenType(token);
        if (!"access".equals(tokenType)) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // Redis 블랙리스트(로그아웃된 토큰) 검사 — 키는 토큰 원문이 아닌 'BL:<sha256>' 형태
        String isLogout = redisTemplate.opsForValue().get(JwtTokenProvider.blacklistKey(token));
        if (isLogout != null) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // 정상 토큰 — SecurityContext 에 인증 정보 저장 후 다음 필터로 위임
        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    // 401 JSON 응답을 직접 작성한다 — Spring Security 의 anonymous 진입 전이라 GlobalExceptionHandler 를 거치지 않음
    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
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
