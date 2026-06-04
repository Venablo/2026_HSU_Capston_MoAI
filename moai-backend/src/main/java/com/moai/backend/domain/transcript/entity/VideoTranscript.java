package com.moai.backend.domain.transcript.entity;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "video_transcripts", indexes = {
        @Index(name = "idx_video_transcripts_curriculum_time",
                columnList = "curriculum_id, start_sec, end_sec")
})
public class VideoTranscript {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private WeeklyCurriculum curriculum;

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

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public VideoTranscript(WeeklyCurriculum curriculum, String videoId,
                            BigDecimal startSec, BigDecimal endSec,
                            String textContent, Integer chunkIndex) {
        this.curriculum = curriculum;
        this.videoId = videoId;
        this.startSec = startSec;
        this.endSec = endSec;
        this.textContent = textContent;
        this.chunkIndex = chunkIndex;
    }
}
