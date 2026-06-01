package com.moai.backend.domain.quiz.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mock_final_quizzes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mock_final_quizzes_subject_week",
                        columnNames = {"subject", "week_number"})
        })
public class MockFinalQuiz {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // 학습실 subject 와 매칭되는 시연용 키
    @Column(name = "subject", nullable = false, length = 100)
    private String subject;

    @Column(name = "week_number", nullable = false)
    private Short weekNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public MockFinalQuiz(String subject, Short weekNumber, String title) {
        this.subject = subject;
        this.weekNumber = weekNumber;
        this.title = title;
    }
}
