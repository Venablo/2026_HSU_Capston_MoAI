package com.moai.backend.domain.onboarding.service;

import com.moai.backend.domain.onboarding.dto.OnboardingKeywordResponseDto;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final UserRepository userRepository;

    public OnboardingKeywordResponseDto getRecommendedKeywords(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return new OnboardingKeywordResponseDto(
                user.getInterestKeywords() != null ? user.getInterestKeywords() : Collections.emptyList()
        );
    }
}
