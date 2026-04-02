package com.moai.backend.domain.onboarding.controller;

import com.moai.backend.domain.onboarding.dto.OnboardingKeywordResponseDto;
import com.moai.backend.domain.onboarding.service.OnboardingService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<OnboardingKeywordResponseDto>> getKeywords(
            @AuthenticationPrincipal UserDetails userDetails) {

        OnboardingKeywordResponseDto responseDto = onboardingService.getRecommendedKeywords(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success(200, "추천 키워드 조회 성공", responseDto));
    }
}
