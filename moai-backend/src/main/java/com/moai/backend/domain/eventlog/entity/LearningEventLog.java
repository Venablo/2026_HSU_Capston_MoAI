package com.moai.backend.domain.eventlog.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.users.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "learning_event_logs", indexes = {
        @Index(name = "idx_event_logs_user_type_triggered",
                columnList = "user_id, event_type, ai_triggered")
})
public class LearningEventLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    // 커리큘럼당 영상이 여러 개이므로 조회/필터링을 위해 별도 컬럼으로 관리
    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    // "video_rewind" | "video_skip" | "video_speed_up" | "video_pause" | "tab_departure"
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    // 이벤트별 부가 정보 JSON (유형마다 구조가 다르므로 String으로 저장)
    @Column(name = "payload", columnDefinition = "json")
    private String payload;

    @Column(name = "ai_triggered", nullable = false)
    private Boolean aiTriggered;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public LearningEventLog(User user, WeeklyCurriculum curriculum, String videoId,
                            String eventType, String payload,
                            Boolean aiTriggered, LocalDateTime loggedAt) {
        this.user = user;
        this.curriculum = curriculum;
        this.videoId = videoId;
        this.eventType = eventType;
        this.payload = payload;
        this.aiTriggered = aiTriggered != null ? aiTriggered : true;
        this.loggedAt = loggedAt != null ? loggedAt : LocalDateTime.now();
    }
}
