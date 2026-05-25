package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {

    // 주차별 퀴즈 응시 이력: quiz → curriculum 조인으로 weekId 기준 조회
    List<QuizAttempt> findByUserIdAndQuiz_CurriculumIdOrderByAttemptedAtDesc(
            String userId, String curriculumId);

    @Modifying
    @Query("DELETE FROM QuizAttempt qa WHERE qa.quiz.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);

    // 시연용 cleanup
    @Modifying
    @Query("DELETE FROM QuizAttempt qa WHERE qa.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
