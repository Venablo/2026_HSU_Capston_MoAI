package com.moai.backend.domain.demo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정보처리기사 시연용 목업 자막 청크.
 * 영구 보관 — cleanupDemoData 의 삭제 대상 아님.
 * template_id 는 mock_curriculum_templates.id 를 참조.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "mock_transcript_templates",
        indexes = {
                @Index(name = "idx_mock_transcript_template", columnList = "template_id, chunk_index")
        })
public class MockTranscriptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Column(name = "start_sec", nullable = false, precision = 10, scale = 3)
    private BigDecimal startSec;

    @Column(name = "end_sec", nullable = false, precision = 10, scale = 3)
    private BigDecimal endSec;

    @Column(name = "text_content", nullable = false, columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MockTranscriptTemplate(Long templateId, String videoId, BigDecimal startSec,
                                   BigDecimal endSec, String textContent, Integer chunkIndex) {
        this.templateId = templateId;
        this.videoId = videoId;
        this.startSec = startSec;
        this.endSec = endSec;
        this.textContent = textContent;
        this.chunkIndex = chunkIndex;
    }
}
