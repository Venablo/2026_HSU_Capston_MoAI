package com.moai.backend.global.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponse {
    private final boolean success;   // 항상 false
    private final String code;       // 에러 코드 (예: AUTH_001)
    private final String message;    // 사용자에게 보여줄 메시지
    private final String timestamp;  // 응답 시각 (ISO 8601)
}