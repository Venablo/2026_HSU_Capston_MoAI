package com.moai.backend.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final int status;
    private final String code;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
    }
}