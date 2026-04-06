package com.moai.backend.domain.quiz.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
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
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_curriculum_id", columnList = "curriculum_id")
})
public class Quiz {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    @Column(name = "quiz_type", nullable = false, length = 15)
    private String quizType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // 돌발 퀴즈 생성 시 되감기 대상 초 (nullable — weekly 퀴즈는 사용하지 않음)
    @Column(name = "rewind_to_sec")
    private Integer rewindToSec;

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
    public Quiz(WeeklyCurriculum curriculum, String quizType, String title, Integer rewindToSec) {
        this.curriculum = curriculum;
        this.quizType = quizType;
        this.title = title;
        this.rewindToSec = rewindToSec;
    }
}
