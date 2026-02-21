package com.moai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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

                // 3. 요청 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health").permitAll() // /health 경로는 누구나 접근 가능
                        .anyRequest().permitAll()               // 개발 초기니 모든 요청을 일단 허용 (나중에 수정 예정)
                );

        return http.build();
    }
}
