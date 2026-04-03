package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizReportRepository extends JpaRepository<QuizReport, String> {

    Optional<QuizReport> findByUserIdAndCurriculumId(String userId, String curriculumId);
}
