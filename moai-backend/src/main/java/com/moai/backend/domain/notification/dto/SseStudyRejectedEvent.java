package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SseStudyRejectedEvent {

    private String type;
    private String message;
    private String suggestionId;
}
