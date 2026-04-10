package com.moai.backend.domain.quiz.entity;

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
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_quiz_attempts_user_id", columnList = "user_id")
})
public class QuizAttempt {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    // 세트 단위 집계용 중복 저장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "selected", columnDefinition = "TEXT")
    private String selected;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @CreatedDate
    @Column(name = "attempted_at", updatable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public QuizAttempt(QuizQuestion question, Quiz quiz, User user,
                        String selected, Boolean isCorrect, String aiExplanation) {
        this.question = question;
        this.quiz = quiz;
        this.user = user;
        this.selected = selected;
        this.isCorrect = isCorrect;
        this.aiExplanation = aiExplanation;
    }
}
