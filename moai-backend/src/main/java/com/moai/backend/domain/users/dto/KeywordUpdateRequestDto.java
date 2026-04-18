package com.moai.backend.domain.users.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class KeywordUpdateRequestDto {

    @NotNull(message = "관심 키워드 배열은 필수입니다.")
    private List<String> interestKeywords;
}
