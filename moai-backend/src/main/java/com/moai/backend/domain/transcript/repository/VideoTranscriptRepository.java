package com.moai.backend.domain.transcript.repository;

import com.moai.backend.domain.transcript.entity.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, String> {

    List<VideoTranscript> findByCurriculumIdAndStartSecLessThanEqualAndEndSecGreaterThanEqual(
            String curriculumId, BigDecimal startSec, BigDecimal endSec);
}
