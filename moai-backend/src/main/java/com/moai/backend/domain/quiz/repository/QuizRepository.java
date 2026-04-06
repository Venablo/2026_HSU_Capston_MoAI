package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByCurriculumId(String curriculumId);

    // 특정 주차의 돌발 퀴즈 중 가장 최근 생성된 1개 조회
    Optional<Quiz> findTopByCurriculumIdAndQuizTypeOrderByCreatedAtDesc(String curriculumId, String quizType);
}
