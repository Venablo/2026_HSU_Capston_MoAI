package com.moai.backend.domain.notification.service;

import com.moai.backend.domain.notification.dto.NotificationListResponseDto;
import com.moai.backend.domain.notification.dto.NotificationReadResponseDto;
import com.moai.backend.domain.notification.entity.Notification;
import com.moai.backend.domain.notification.repository.NotificationRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("SSE 초기 연결 이벤트 전송 실패 - userId: {}", userId, e);
        }

        return emitter;
    }

    public void pushSse(String userId, Object event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(event));
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("SSE 이벤트 전송 실패 - userId: {}", userId, e);
        }
    }

    public List<NotificationListResponseDto> getUnreadNotifications(String email) {
        User user = findUserByEmail(email);

        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(n -> new NotificationListResponseDto(
                        n.getId(),
                        n.getType(),
                        n.getMessage(),
                        n.getReferenceId(),
                        n.getIsRead(),
                        n.getCreatedAt()))
                .toList();
    }

    @Transactional
    public NotificationReadResponseDto markAsRead(String email, String notificationId) {
        User user = findUserByEmail(email);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();

        return new NotificationReadResponseDto(notification.getId(), notification.getIsRead());
    }

    public String getUserIdByEmail(String email) {
        return findUserByEmail(email).getId();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
