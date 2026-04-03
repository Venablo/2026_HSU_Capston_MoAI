package com.moai.backend.domain.quiz.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "quiz_questions", indexes = {
        @Index(name = "idx_quiz_questions_quiz_id", columnList = "quiz_id")
})
public class QuizQuestion {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "question_type", nullable = false, length = 10)
    private String questionType;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    // essay 타입은 선택지 없음 (NULL)
    @Convert(converter = QuizOptionListConverter.class)
    @Column(name = "options", columnDefinition = "json")
    private List<QuizOption> options;

    // essay 타입은 AI가 채점하므로 정답 없음 (NULL)
    @Column(name = "answer", length = 5)
    private String answer;

    @Column(name = "question_order", nullable = false)
    private Short questionOrder;

    @Column(name = "related_keyword", length = 100)
    private String relatedKeyword;

    @Column(name = "time_limit_sec")
    private Short timeLimitSec;

    // essay 타입 전용: 답변 최대 글자 수
    @Column(name = "max_length")
    private Short maxLength;

    // essay 타입 전용: 힌트
    @Column(name = "tip", columnDefinition = "TEXT")
    private String tip;

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
    public QuizQuestion(Quiz quiz, String questionType, String question,
                         List<QuizOption> options, String answer, Short questionOrder,
                         String relatedKeyword, Short timeLimitSec, Short maxLength, String tip) {
        this.quiz = quiz;
        this.questionType = questionType;
        this.question = question;
        this.options = options;
        this.answer = answer;
        this.questionOrder = questionOrder;
        this.relatedKeyword = relatedKeyword;
        this.timeLimitSec = timeLimitSec;
        this.maxLength = maxLength;
        this.tip = tip;
    }
}
