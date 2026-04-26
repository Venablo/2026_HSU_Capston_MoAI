package com.moai.backend.domain.quiz.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.users.entity.User;
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
@Table(name = "quiz_reports", indexes = {
        @Index(name = "idx_quiz_reports_user_curriculum", columnList = "user_id, curriculum_id")
})
public class QuizReport {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    @Column(name = "final_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal finalScore;

    // 레이더차트 데이터 — 키가 동적이므로 raw JSON 저장 (예: {"개념이해도":90,"응용력":85})
    @Column(name = "radar_data", columnDefinition = "json")
    private String radarData;

    // 문항별 AI 해설 배열 — 구조가 동적이므로 raw JSON 저장
    @Column(name = "questions", columnDefinition = "json")
    private String questions;

    @Column(name = "status", nullable = false, length = 15)
    private String status;

    @Column(name = "estimated_sec")
    private Short estimatedSec;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public QuizReport(Quiz quiz, User user, WeeklyCurriculum curriculum,
                       BigDecimal finalScore, String radarData, String questions,
                       String status, Short estimatedSec) {
        this.quiz = quiz;
        this.user = user;
        this.curriculum = curriculum;
        this.finalScore = finalScore;
        this.radarData = radarData;
        this.questions = questions;
        this.status = status;
        this.estimatedSec = estimatedSec;
    }

    public void complete(BigDecimal finalScore, String radarData, String questions) {
        this.finalScore = finalScore;
        this.radarData = radarData;
        this.questions = questions;
        this.status = "completed";
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = "failed";
        this.completedAt = LocalDateTime.now();
    }
}
