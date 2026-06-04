package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {

    List<QuizQuestion> findByQuizIdOrderByQuestionOrder(String quizId);

    @Modifying
    @Query("DELETE FROM QuizQuestion qq WHERE qq.quiz.curriculum.id IN :curriculumIds")
    void deleteByCurriculumIdIn(@Param("curriculumIds") List<String> curriculumIds);
}
