package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {

    // 주차별 퀴즈 응시 이력: quiz → curriculum 조인으로 weekId 기준 조회
    List<QuizAttempt> findByUserIdAndQuiz_CurriculumIdOrderByAttemptedAtDesc(
            String userId, String curriculumId);
}
