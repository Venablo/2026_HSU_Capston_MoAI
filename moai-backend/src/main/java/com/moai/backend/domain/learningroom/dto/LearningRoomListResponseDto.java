package com.moai.backend.domain.learningroom.dto;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class LearningRoomListResponseDto {

    private List<RoomSummary> rooms;

    @Getter
    @AllArgsConstructor
    public static class RoomSummary {
        private String roomId;
        private String subject;
        private String level;
        private int durationWeeks;
        private int currentWeek;
        private BigDecimal completionRate;
        private String status;
        private LocalDateTime createdAt;

        public static RoomSummary from(LearningRoom room) {
            return new RoomSummary(
                    room.getId(),
                    room.getSubject(),
                    room.getLevel(),
                    room.getDurationWeeks(),
                    room.getCurrentWeek(),
                    room.getCompletionRate(),
                    room.getStatus(),
                    room.getCreatedAt()
            );
        }
    }
}
