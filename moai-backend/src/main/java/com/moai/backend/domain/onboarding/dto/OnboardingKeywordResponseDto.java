package com.moai.backend.domain.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OnboardingKeywordResponseDto {

    private List<String> keywords;
}
