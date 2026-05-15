package com.moai.backend.domain.keyword.dto;

import com.moai.backend.domain.keyword.entity.UserKeyword;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class KeywordListResponseDto {

    private List<StrengthKeyword> strengths;
    private List<WeaknessKeyword> weaknesses;

    @Getter
    @AllArgsConstructor
    public static class StrengthKeyword {
        private String keyword;

        public static StrengthKeyword from(UserKeyword uk) {
            return new StrengthKeyword(uk.getKeyword());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class WeaknessKeyword {
        private String keyword;
        private Short weaknessCount;
        private Boolean isResolved;

        public static WeaknessKeyword from(UserKeyword uk) {
            return new WeaknessKeyword(uk.getKeyword(), uk.getWeaknessCount(), uk.getIsResolved());
        }
    }
}
