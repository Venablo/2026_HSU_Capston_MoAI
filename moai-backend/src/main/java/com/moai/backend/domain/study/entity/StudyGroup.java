package com.moai.backend.domain.study.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "study_groups")
public class StudyGroup {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "match_keyword", length = 100)
    private String matchKeyword;

    @Column(name = "match_reason", columnDefinition = "TEXT")
    private String matchReason;

    @Column(name = "match_score", precision = 4, scale = 3)
    private BigDecimal matchScore;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

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
    public StudyGroup(String type, String subject, String matchKeyword,
                      String matchReason, BigDecimal matchScore, String status) {
        this.type = type;
        this.subject = subject;
        this.matchKeyword = matchKeyword;
        this.matchReason = matchReason;
        this.matchScore = matchScore;
        this.status = status;
    }

    public void activate() {
        this.status = "active";
    }

    public void disband() {
        this.status = "disbanded";
    }
}
