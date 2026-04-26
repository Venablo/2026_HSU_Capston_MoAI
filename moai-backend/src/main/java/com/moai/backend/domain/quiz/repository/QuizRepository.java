package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByCurriculumId(String curriculumId);

    // 특정 주차의 돌발 퀴즈 중 가장 최근 생성된 1개 조회
    Optional<Quiz> findTopByCurriculumIdAndQuizTypeOrderByCreatedAtDesc(String curriculumId, String quizType);

    // 특정 주차의 퀴즈 타입별 조회 (weekly 파이널 퀴즈 등)
    Optional<Quiz> findByCurriculumIdAndQuizType(String curriculumId, String quizType);

    @Modifying
    @Query("DELETE FROM Quiz q WHERE q.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);
}
