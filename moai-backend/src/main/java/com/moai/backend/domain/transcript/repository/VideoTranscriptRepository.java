package com.moai.backend.domain.transcript.repository;

import com.moai.backend.domain.transcript.entity.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, String> {

    List<VideoTranscript> findByCurriculumIdAndStartSecLessThanEqualAndEndSecGreaterThanEqual(
            String curriculumId, BigDecimal startSec, BigDecimal endSec);

    // 패턴1/2/3/4 공통: 범위와 겹치는 자막 조회 (start_sec <= toSec AND end_sec >= fromSec)
    List<VideoTranscript> findByCurriculumIdAndVideoIdAndStartSecLessThanEqualAndEndSecGreaterThanEqualOrderByChunkIndex(
            String curriculumId, String videoId, BigDecimal toSec, BigDecimal fromSec);

    // 글로벌 자막 캐시 hit 확인 — 어떤 curriculum 이든 한 번이라도 이 videoId 의 자막이 저장됐는지.
    // 가장 먼저 만난 chunk 한 개로 출처 curriculum 을 식별한다.
    Optional<VideoTranscript> findFirstByVideoIdOrderByChunkIndexAsc(String videoId);

    // 출처 curriculum 의 전체 청크 로드 (복제 소스).
    List<VideoTranscript> findByCurriculumIdAndVideoIdOrderByChunkIndexAsc(String curriculumId, String videoId);

    @Modifying
    @Query("DELETE FROM VideoTranscript vt WHERE vt.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);

    // 시연용 cleanup: 해당 사용자의 모든 학습실 하위 자막 일괄 삭제
    @Modifying
    @Query("DELETE FROM VideoTranscript vt WHERE vt.curriculum.room.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
