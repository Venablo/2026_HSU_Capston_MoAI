package com.moai.backend.domain.transcript.repository;

import com.moai.backend.domain.transcript.entity.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, String> {

    List<VideoTranscript> findByCurriculumIdAndStartSecLessThanEqualAndEndSecGreaterThanEqual(
            String curriculumId, BigDecimal startSec, BigDecimal endSec);

    // 패턴1/2/3/4 공통: 범위와 겹치는 자막 조회 (start_sec <= toSec AND end_sec >= fromSec)
    List<VideoTranscript> findByCurriculumIdAndVideoIdAndStartSecLessThanEqualAndEndSecGreaterThanEqualOrderByChunkIndex(
            String curriculumId, String videoId, BigDecimal toSec, BigDecimal fromSec);
}
