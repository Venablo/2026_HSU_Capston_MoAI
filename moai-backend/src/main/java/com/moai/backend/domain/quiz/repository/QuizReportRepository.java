package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizReportRepository extends JpaRepository<QuizReport, String> {

    Optional<QuizReport> findByUserIdAndCurriculumId(String userId, String curriculumId);

    @Modifying
    @Query("DELETE FROM QuizReport qr WHERE qr.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);

    // 시연용 cleanup
    @Modifying
    @Query("DELETE FROM QuizReport qr WHERE qr.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
