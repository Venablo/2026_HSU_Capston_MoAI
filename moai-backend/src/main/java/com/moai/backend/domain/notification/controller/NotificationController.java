package com.moai.backend.domain.notification.controller;

import com.moai.backend.domain.notification.dto.NotificationListResponseDto;
import com.moai.backend.domain.notification.dto.NotificationReadResponseDto;
import com.moai.backend.domain.notification.service.NotificationService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        String userId = notificationService.getUserIdByEmail(email);
        return notificationService.subscribe(userId);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationListResponseDto>>> getUnreadNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<NotificationListResponseDto> responseDto =
                notificationService.getUnreadNotifications(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success("읽지 않은 알림 목록 조회 완료", responseDto));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponseDto>> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        NotificationReadResponseDto responseDto =
                notificationService.markAsRead(userDetails.getUsername(), id);

        return ResponseEntity.ok(ApiResponse.success("알림 읽음 처리 완료", responseDto));
    }
}
