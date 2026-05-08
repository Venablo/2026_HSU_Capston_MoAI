package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SseStudyGroupActivatedEvent {

    private String type;
    private String message;
    private String groupId;
    private String roomId;
    private String curriculumId;
}
