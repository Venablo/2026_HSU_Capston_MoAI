package com.moai.backend.domain.users.dto;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LearningHistoryResponseDto {
    private String roomId;
    private String subject;
    private String level;
    private BigDecimal completionRate;
    private String status;
    private LocalDateTime createdAt;

    public static LearningHistoryResponseDto from(LearningRoom room) {
        return new LearningHistoryResponseDto(
                room.getId(),
                room.getSubject(),
                room.getLevel(),
                room.getCompletionRate(),
                room.getStatus(),
                room.getCreatedAt()
        );
    }
}
