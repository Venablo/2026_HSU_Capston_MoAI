package com.moai.backend.domain.chat.entity;

import com.moai.backend.domain.study.entity.StudyGroup;
import com.moai.backend.domain.users.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_messages",
        indexes = @Index(name = "idx_study_messages_group_sent", columnList = "group_id, sent_at"))
public class StudyMessage {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(name = "sender_type", nullable = false, length = 5)
    private String senderType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_ai_correction", nullable = false)
    private Boolean isAiCorrection;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public StudyMessage(StudyGroup group, User sender, String senderType,
                        String content, Boolean isAiCorrection) {
        this.group = group;
        this.sender = sender;
        this.senderType = senderType;
        this.content = content;
        this.isAiCorrection = isAiCorrection != null ? isAiCorrection : false;
        this.sentAt = LocalDateTime.now();
    }
}
