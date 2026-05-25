package com.moai.backend.domain.eventlog.repository;

import com.moai.backend.domain.eventlog.entity.LearningEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearningEventLogRepository extends JpaRepository<LearningEventLog, String> {

    @Modifying
    @Query("DELETE FROM LearningEventLog el WHERE el.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);

    // 시연용 cleanup
    @Modifying
    @Query("DELETE FROM LearningEventLog el WHERE el.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
