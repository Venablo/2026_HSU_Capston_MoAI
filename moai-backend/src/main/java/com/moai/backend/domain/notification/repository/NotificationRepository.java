package com.moai.backend.domain.notification.repository;

import com.moai.backend.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);
}
