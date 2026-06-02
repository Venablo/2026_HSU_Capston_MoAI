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
@Table(name = "mock_final_quiz_answers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mock_final_quiz_answers_quiz_order",
                        columnNames = {"mock_quiz_id", "question_order"})
        })
public class MockFinalQuizAnswer {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // mock_final_quizzes.id FK (객체 참조 대신 컬럼만 보관)
    @Column(name = "mock_quiz_id", nullable = false, length = 36)
    private String mockQuizId;

    // mock_final_quiz_questions.question_order 와 짝
    @Column(name = "question_order", nullable = false)
    private Short questionOrder;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public MockFinalQuizAnswer(String mockQuizId, Short questionOrder, String answerText) {
        this.mockQuizId = mockQuizId;
        this.questionOrder = questionOrder;
        this.answerText = answerText;
    }
}
