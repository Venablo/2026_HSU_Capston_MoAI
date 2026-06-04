package com.moai.backend.domain.keyword.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.users.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_keywords", indexes = {
        @Index(name = "idx_user_keywords_matching",
                columnList = "keyword, keyword_type, weakness_count"),
        @Index(name = "idx_user_keywords_user_room",
                columnList = "user_id, room_id")
})
public class UserKeyword {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private LearningRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    // "strength" | "weakness"
    @Column(name = "keyword_type", nullable = false, length = 10)
    private String keywordType;

    @Column(name = "weakness_count", nullable = false)
    private Short weaknessCount;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public UserKeyword(User user, LearningRoom room, WeeklyCurriculum curriculum,
                       String keyword, String keywordType) {
        this.user = user;
        this.room = room;
        this.curriculum = curriculum;
        this.keyword = keyword;
        this.keywordType = keywordType;
        this.weaknessCount = (short) 1;
        this.isResolved = false;
    }

    /**
     * 약점 키워드 누적 횟수를 1 증가시킨다.
     */
    public void incrementWeaknessCount() {
        this.weaknessCount = (short) (this.weaknessCount + 1);
    }

    /**
     * 약점 키워드를 해소 처리한다. (거꾸로 학습에서 gainedKeywords로 판정된 경우)
     */
    public void resolve() {
        this.isResolved = true;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 강점으로 해소됐던 약점이 같은 주차에서 다시 약점으로 잡힌 경우 호출.
     * 누적 카운트를 1로 리셋하고 해소 플래그를 해제한다.
     */
    public void reactivateAsWeakness() {
        this.isResolved = false;
        this.resolvedAt = null;
        this.weaknessCount = (short) 1;
    }

    /**
     * 약점 재발로 무효화됐던 강점이 다시 강점으로 잡힌 경우 호출.
     * 해소 플래그만 해제한다.
     */
    public void unresolve() {
        this.isResolved = false;
        this.resolvedAt = null;
    }
}
