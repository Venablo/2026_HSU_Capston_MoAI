package com.moai.backend.global.subtitle.exception;

import lombok.Getter;

/**
 * 자막 스크래핑 실패를 전달하는 예외.
 *
 * GlobalExceptionHandler 가 errorCode.getHttpStatus() 를 기반으로 응답 상태를 결정하므로,
 * 호출자는 SubtitleErrorCode 종류에 따라 분기 처리할 수 있다.
 * 예: NO_SUBTITLES_AVAILABLE → 다른 영상 재선정, RATE_LIMITED → 재시도 큐 등록.
 */
@Getter
public class SubtitleScrapeException extends RuntimeException {

    private final SubtitleErrorCode errorCode;
    private final String detail;

    public SubtitleScrapeException(SubtitleErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"));
        this.errorCode = errorCode;
        this.detail = detail == null ? "" : detail;
    }

    public SubtitleScrapeException(SubtitleErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"), cause);
        this.errorCode = errorCode;
        this.detail = detail == null ? "" : detail;
    }
}
