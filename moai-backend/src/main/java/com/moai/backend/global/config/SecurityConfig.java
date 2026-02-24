package com.moai.backend.global.config;

import com.moai.backend.global.auth.JwtAuthenticationFilter;
import com.moai.backend.global.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
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
                        .requestMatchers("/api/auth/**").permitAll() // 로그인, 회원가입은 티켓 없이 통과
                        .requestMatchers("/health").permitAll() // /health 경로는 누구나 접근 가능
                        .anyRequest().authenticated()               // 나머지는 무조건 토큰 검사
                )

                // 5. JWT 필터를 기존 시큐리티 필터 체인에 끼워 넣기
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
