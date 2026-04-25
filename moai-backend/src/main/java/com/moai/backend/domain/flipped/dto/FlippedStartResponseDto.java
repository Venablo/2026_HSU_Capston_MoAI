package com.moai.backend.domain.flipped.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FlippedStartResponseDto {

    private String sessionId;
    private String firstMessage;
}
