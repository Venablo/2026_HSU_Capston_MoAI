package com.moai.backend.global.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private final int status;    // HTTP 상태 코드 (예: 400)
    private final String code;   // 우리가 정한 에러 코드 (예: AUTH_001)
    private final String message; // 사용자에게 보여줄 메시지
}