package com.moai.backend.domain.users.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class KeywordUpdateResponseDto {
    private List<String> interestKeywords;
}
