package com.moai.backend.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice //  하나의 클래스로 모든 컨트롤러에 대해 전역적으로 예외 처리
public class GlobalExceptionHandler {

    // 유효성 검사 실패(이메일 형식 올바르지 않음, 이메일 미기입, 비밀번호 미기입)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        // 에러 메시지 중 첫 번째 항목만 가져와서 전달
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code("COMMON_001")
                .message(message)
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 비즈니스 예외(CustomException)를 통합 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(e.getCode())
                .message(e.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(e.getStatus()).body(response);
    }
}