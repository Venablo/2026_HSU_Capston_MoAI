package com.moai.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.global.auth.JwtAuthenticationFilter;
import com.moai.backend.global.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 0. CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 1. CSRF 보안 기능 끄기 (로컬 개발 시에는 번거롭기 때문)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. HTTP 기본 인증 끄기 (로그인 창 제거)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // 3. 서버에 세션을 만들지 않고, 오직 토큰으로만 인증
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. 요청 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // SSE 비동기 디스패치 시 Security 재필터링 방지
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/actuator/health").permitAll() // 공개 API
                        .requestMatchers("/api/files/**").permitAll() // 로컬 파일 서빙 (S3 비활성화 시 폴백)
                        .requestMatchers("/ws/study-groups/**").permitAll() // WebSocket 핸드셰이크 허용 (인증은 StompChannelInterceptor에서 처리)
                        .anyRequest().authenticated()               // 로그아웃 포함 나머지는 JWT 필요
                )

                // 5. JWT 필터를 기존 시큐리티 필터 체인에 끼워 넣기
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Authorization-Refresh"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
