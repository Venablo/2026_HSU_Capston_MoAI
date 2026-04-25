package com.moai.backend.domain.eventlog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class EventRequestDto {

    @NotBlank
    @JsonProperty("event_type")
    private String eventType;

    @NotBlank
    @JsonProperty("curriculum_id")
    private String curriculumId;

    @NotNull
    private Map<String, Object> payload;
}
