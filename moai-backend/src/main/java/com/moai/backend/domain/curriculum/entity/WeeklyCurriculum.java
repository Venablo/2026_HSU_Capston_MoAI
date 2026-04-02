package com.moai.backend.domain.curriculum.entity;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
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
@Table(name = "weekly_curriculums",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_room_week", columnNames = {"room_id", "week_number"})
        },
        indexes = {
                @Index(name = "idx_weekly_curriculums_room_id", columnList = "room_id")
        })
public class WeeklyCurriculum {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private LearningRoom room;

    @Column(name = "week_number", nullable = false)
    private Short weekNumber;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "keywords", columnDefinition = "json")
    private List<String> keywords;

    @Convert(converter = ResourceListConverter.class)
    @Column(name = "resources", columnDefinition = "json")
    private List<CurriculumResource> resources;

    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionRate;

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
    public WeeklyCurriculum(LearningRoom room, Short weekNumber, String topic,
                             String description, List<String> keywords,
                             List<CurriculumResource> resources) {
        this.room = room;
        this.weekNumber = weekNumber;
        this.topic = topic;
        this.description = description;
        this.keywords = keywords;
        this.resources = resources;
        this.completionRate = BigDecimal.ZERO;
    }

    // --- 비동기 enrichment에서 호출하는 업데이트 메서드 ---

    public void updateResources(List<CurriculumResource> resources) {
        this.resources = resources;
    }

    public void updateKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
