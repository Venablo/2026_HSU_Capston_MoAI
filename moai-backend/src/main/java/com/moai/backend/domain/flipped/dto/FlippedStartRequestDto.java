package com.moai.backend.domain.flipped.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FlippedStartRequestDto {

    @NotBlank(message = "커리큘럼 ID는 필수입니다.")
    @JsonProperty("curriculum_id")
    private String curriculumId;
}
