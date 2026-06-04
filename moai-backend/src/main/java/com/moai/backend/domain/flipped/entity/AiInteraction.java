package com.moai.backend.domain.flipped.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.users.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ai_interactions", indexes = {
        // 세션별 대화 이력 조회용 인덱스
        @Index(name = "idx_ai_interactions_session", columnList = "session_id"),
        // SSE 대화 이력 시간순 조회용 인덱스
        @Index(name = "idx_ai_interactions_room_created", columnList = "room_id, created_at")
})
public class AiInteraction {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // 거꾸로 학습 세션 구분자 — 세션 시작 시 UUID 발급
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private LearningRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    // "user" | "assistant"
    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // AI 역질문 여부 — 프론트에서 역질문 UI를 별도로 표시하기 위한 플래그
    @Column(name = "is_counter_question", nullable = false)
    private Boolean isCounterQuestion;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public AiInteraction(String sessionId, User user, LearningRoom room,
                         WeeklyCurriculum curriculum, String role,
                         String content, Boolean isCounterQuestion) {
        this.sessionId = sessionId;
        this.user = user;
        this.room = room;
        this.curriculum = curriculum;
        this.role = role;
        this.content = content;
        this.isCounterQuestion = isCounterQuestion != null ? isCounterQuestion : false;
    }
}
