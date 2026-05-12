package com.moai.backend.global.exception;

import com.moai.backend.global.subtitle.exception.SubtitleScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
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

    // 자막 스크래핑 예외 — errorCode 의 HTTP status / code / message 를 그대로 응답에 매핑
    @ExceptionHandler(SubtitleScrapeException.class)
    public ResponseEntity<ErrorResponse> handleSubtitleScrapeException(SubtitleScrapeException e) {
        log.warn("자막 스크래핑 실패: code={}, detail={}", e.getErrorCode(), e.getDetail());

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code(e.getErrorCode().getCode())
                .message(e.getErrorCode().getMessage())
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(response);
    }

    // 예상치 못한 런타임 예외 — 스택트레이스 노출 방지 + 통일된 ErrorResponse 반환
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("처리되지 않은 예외 발생", e);

        ErrorResponse response = ErrorResponse.builder()
                .success(false)
                .code("COMMON_500")
                .message("서버 내부 오류가 발생했습니다.")
                .timestamp(LocalDateTime.now().toString())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}