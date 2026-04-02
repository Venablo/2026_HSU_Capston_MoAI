package com.moai.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private int status;      // HTTP 상태 코드
    private String message;  // 응답 메시지
    private T data;          // 결과 데이터 (토큰 DTO 등)

    // 1. 데이터가 있는 성공 응답 (로그인, 조회 등)
    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    // 2. 데이터가 없는 성공 응답 (로그아웃, 삭제 등)
    public static <T> ApiResponse<T> success(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}