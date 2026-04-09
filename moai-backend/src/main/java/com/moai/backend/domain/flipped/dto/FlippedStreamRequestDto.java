package com.moai.backend.domain.flipped.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FlippedStreamRequestDto {

    @NotBlank(message = "세션 ID는 필수입니다.")
    private String sessionId;

    @NotBlank(message = "메시지는 필수입니다.")
    private String message;
}
