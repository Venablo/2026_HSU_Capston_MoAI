package com.moai.backend.domain.learningroom.entity;

import com.moai.backend.domain.users.entity.User;
import com.moai.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "learning_rooms", indexes = {
        @Index(name = "idx_learning_rooms_user_id", columnList = "user_id")
})
public class LearningRoom extends BaseTimeEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "subject", nullable = false, length = 100)
    private String subject;

    @Column(name = "level", nullable = false, length = 20)
    private String level;

    @Column(name = "duration_weeks", nullable = false)
    private Short durationWeeks;

    @Column(name = "hours_per_day", nullable = false, precision = 4, scale = 1)
    private BigDecimal hoursPerDay;

    @Column(name = "current_week", nullable = false)
    private Short currentWeek;

    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionRate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public LearningRoom(User user, String subject, String level,
                         Short durationWeeks, BigDecimal hoursPerDay) {
        this.user = user;
        this.subject = subject;
        this.level = level;
        this.durationWeeks = durationWeeks;
        this.hoursPerDay = hoursPerDay;
        this.currentWeek = 1;
        this.completionRate = BigDecimal.ZERO;
        this.status = "active";
    }
}
