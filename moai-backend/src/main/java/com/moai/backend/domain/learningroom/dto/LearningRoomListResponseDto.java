package com.moai.backend.domain.learningroom.dto;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class LearningRoomListResponseDto {

    private String roomId;
    private String subject;
    private String level;
    private int currentWeek;
    private int durationWeeks;
    private BigDecimal completionRate;
    private String status;

    public static LearningRoomListResponseDto from(LearningRoom room) {
        return LearningRoomListResponseDto.builder()
                .roomId(room.getId())
                .subject(room.getSubject())
                .level(room.getLevel())
                .currentWeek(room.getCurrentWeek())
                .durationWeeks(room.getDurationWeeks())
                .completionRate(room.getCompletionRate())
                .status(room.getStatus())
                .build();
    }
}
