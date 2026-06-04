package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SseSimpleEvent {

    private String type;
    private String message;
}
