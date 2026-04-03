package com.moai.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;   // 성공 여부
    private T data;            // 결과 데이터
    private String message;    // 응답 메시지
    private String timestamp;  // 응답 시각 (ISO 8601)

    // 1. 데이터가 있는 성공 응답 (로그인, 조회 등)
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now().toString());
    }

    // 2. 데이터가 없는 성공 응답 (로그아웃, 삭제 등)
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, null, message, LocalDateTime.now().toString());
    }
}