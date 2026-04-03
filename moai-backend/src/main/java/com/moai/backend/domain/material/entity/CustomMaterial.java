package com.moai.backend.domain.material.entity;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "custom_materials", indexes = {
        @Index(name = "idx_custom_materials_room_id", columnList = "room_id")
})
public class CustomMaterial {

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

    @Convert(converter = StringListConverter.class)
    @Column(name = "trigger_keywords", columnDefinition = "json")
    private List<String> triggerKeywords;

    @Column(name = "video_segment", length = 50)
    private String videoSegment;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Convert(converter = SummaryItemListConverter.class)
    @Column(name = "summary_items", columnDefinition = "json")
    private List<SummaryItem> summaryItems;

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
    public CustomMaterial(User user, LearningRoom room, WeeklyCurriculum curriculum,
                          List<String> triggerKeywords, String videoSegment,
                          String title, List<SummaryItem> summaryItems) {
        this.user = user;
        this.room = room;
        this.curriculum = curriculum;
        this.triggerKeywords = triggerKeywords;
        this.videoSegment = videoSegment;
        this.title = title;
        this.summaryItems = summaryItems;
    }
}
