package com.moai.backend.domain.flipped.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FlippedEndRequestDto {

    @NotBlank(message = "세션 ID는 필수입니다.")
    private String sessionId;
}
