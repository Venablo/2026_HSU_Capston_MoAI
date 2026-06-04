package com.moai.backend.domain.learningroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class LearningRoomCreateResponseDto {

    private String roomId;
    private String subject;
    private String level;
    private int durationWeeks;
    private List<CurriculumSummary> curriculum;

    @Getter
    @AllArgsConstructor
    public static class CurriculumSummary {
        private int weekNumber;
        private String topic;
    }
}
