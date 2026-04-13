package com.moai.backend.domain.study.entity;

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
@Table(name = "study_suggestions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "suggested_to"}))
public class StudySuggestion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_to", nullable = false)
    private User suggestedTo;

    @Column(name = "suggested_role", nullable = false, length = 10)
    private String suggestedRole;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

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
    public StudySuggestion(StudyGroup group, User suggestedTo, String suggestedRole) {
        this.group = group;
        this.suggestedTo = suggestedTo;
        this.suggestedRole = suggestedRole;
        this.status = "pending";
    }

    public void accept() {
        this.status = "accepted";
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = "rejected";
        this.respondedAt = LocalDateTime.now();
    }
}
