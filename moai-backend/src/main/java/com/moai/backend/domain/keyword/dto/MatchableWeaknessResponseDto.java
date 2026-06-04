package com.moai.backend.domain.keyword.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchableWeaknessResponseDto {

    // 해당 주차에 매칭 가능한 약점(미해소 + 누적 3회 이상)이 하나라도 있으면 true
    private boolean hasMatchableWeakness;
}
