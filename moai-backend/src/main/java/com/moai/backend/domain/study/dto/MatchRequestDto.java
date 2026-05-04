package com.moai.backend.domain.study.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MatchRequestDto {

    @NotBlank
    private String curriculumId;
}
