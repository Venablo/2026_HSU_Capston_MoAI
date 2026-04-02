package com.moai.backend.global.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class LlmRequestDto {
    private final String systemPrompt;
    private final String userMessage;
}
