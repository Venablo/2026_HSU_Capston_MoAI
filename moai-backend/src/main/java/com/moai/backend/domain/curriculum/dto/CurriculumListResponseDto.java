package com.moai.backend.domain.curriculum.dto;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class CurriculumListResponseDto {

    private List<CurriculumSummary> weeks;

    @Getter
    @AllArgsConstructor
    public static class CurriculumSummary {
        private String weekId;
        private int weekNumber;
        private String topic;
        private BigDecimal completionRate;
        private boolean isLocked;

        public static CurriculumSummary from(WeeklyCurriculum curriculum, short currentWeek) {
            return new CurriculumSummary(
                    curriculum.getId(),
                    curriculum.getWeekNumber(),
                    curriculum.getTopic(),
                    curriculum.getCompletionRate(),
                    curriculum.getWeekNumber() > currentWeek
            );
        }
    }
}
