package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByCurriculumId(String curriculumId);
}
