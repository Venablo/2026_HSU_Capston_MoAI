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
@Table(name = "mock_final_quiz_questions", indexes = {
        @Index(name = "idx_mock_final_quiz_questions_quiz_id", columnList = "mock_quiz_id")
})
public class MockFinalQuizQuestion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_quiz_id", nullable = false)
    private MockFinalQuiz mockQuiz;

    @Column(name = "question_order", nullable = false)
    private Short questionOrder;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "related_keyword", length = 100)
    private String relatedKeyword;

    @Column(name = "max_length")
    private Short maxLength;

    @Column(name = "tip", columnDefinition = "TEXT")
    private String tip;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public MockFinalQuizQuestion(MockFinalQuiz mockQuiz, Short questionOrder,
                                  String question, String relatedKeyword,
                                  Short maxLength, String tip) {
        this.mockQuiz = mockQuiz;
        this.questionOrder = questionOrder;
        this.question = question;
        this.relatedKeyword = relatedKeyword;
        this.maxLength = maxLength;
        this.tip = tip;
    }
}
