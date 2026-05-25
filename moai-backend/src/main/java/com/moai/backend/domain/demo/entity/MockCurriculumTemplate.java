package com.moai.backend.domain.demo.entity;

import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.ResourceListConverter;
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

/**
 * 정보처리기사 시연용 목업 커리큘럼 템플릿.
 * 영구 보관 — cleanupDemoData 의 삭제 대상 아님.
 * WeeklyCurriculum 과 동일한 컨버터를 사용하여 필드 매핑만으로 복사 가능.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "mock_curriculum_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mock_curriculum_week", columnNames = "week_number")
        })
public class MockCurriculumTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "week_number", nullable = false)
    private Short weekNumber;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "keywords", nullable = false, columnDefinition = "json")
    private List<String> keywords;

    @Convert(converter = ResourceListConverter.class)
    @Column(name = "resources", nullable = false, columnDefinition = "json")
    private List<CurriculumResource> resources;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MockCurriculumTemplate(Short weekNumber, String topic, String description,
                                   List<String> keywords, List<CurriculumResource> resources) {
        this.weekNumber = weekNumber;
        this.topic = topic;
        this.description = description;
        this.keywords = keywords;
        this.resources = resources;
    }
}
