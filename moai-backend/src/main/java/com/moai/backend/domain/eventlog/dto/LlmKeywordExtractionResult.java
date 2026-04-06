package com.moai.backend.domain.eventlog.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class LlmKeywordExtractionResult {

    private List<String> keywords;
}
