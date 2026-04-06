package com.moai.backend.domain.eventlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class EventRequestDto {

    @NotBlank
    private String eventType;

    @NotBlank
    private String curriculumId;

    @NotNull
    private Map<String, Object> payload;
}
