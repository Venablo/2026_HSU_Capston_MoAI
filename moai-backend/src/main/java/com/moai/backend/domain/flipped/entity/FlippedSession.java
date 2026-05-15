package com.moai.backend.domain.flipped.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.global.common.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flipped_sessions", indexes = {
        // 학습실별 거꾸로 학습 결과 조회용 인덱스
        @Index(name = "idx_flipped_sessions_user_room", columnList = "user_id, room_id")
})
public class FlippedSession {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // AI_INTERACTIONS.session_id와 연결되는 세션 식별자
    @Column(name = "session_id", nullable = false, length = 36, unique = true)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private LearningRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    // "pass"(60+) | "partial"(30~59) | "fail"(<30) — pass/partial→강점 키워드 추출 / fail→약점 키워드 추출
    @Column(name = "flipped_result", nullable = false, length = 10)
    private String flippedResult;

    // 이해도 점수 0.00~100.00
    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    // AI 최종 피드백 문구
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    // 거꾸로 학습에서 획득한 강점 키워드 JSON 배열
    @Convert(converter = StringListConverter.class)
    @Column(name = "gained_keywords", columnDefinition = "json")
    private List<String> gainedKeywords;

    // 거꾸로 학습에서 발견된 약점 키워드 JSON 배열
    @Convert(converter = StringListConverter.class)
    @Column(name = "weak_keywords", columnDefinition = "json")
    private List<String> weakKeywords;

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
    public FlippedSession(String sessionId, User user, LearningRoom room,
                          WeeklyCurriculum curriculum, String flippedResult,
                          BigDecimal score, String feedback,
                          List<String> gainedKeywords, List<String> weakKeywords) {
        this.sessionId = sessionId;
        this.user = user;
        this.room = room;
        this.curriculum = curriculum;
        this.flippedResult = flippedResult;
        this.score = score;
        this.feedback = feedback;
        this.gainedKeywords = gainedKeywords;
        this.weakKeywords = weakKeywords;
    }
}
