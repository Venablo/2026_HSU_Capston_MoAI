package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationListResponseDto {

    private String notificationId;
    private String type;
    private String message;
    private String referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
