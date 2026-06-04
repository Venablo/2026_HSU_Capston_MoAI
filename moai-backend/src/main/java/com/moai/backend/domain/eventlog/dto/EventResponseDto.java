package com.moai.backend.domain.eventlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponseDto {

    private boolean aiTriggered;
    private String eventType;
    private List<String> extractedKeywords;
    private String materialId;

    public static EventResponseDto notTriggered() {
        return EventResponseDto.builder()
                .aiTriggered(false)
                .build();
    }
}
