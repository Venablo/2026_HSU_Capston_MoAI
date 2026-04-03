package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {

    List<QuizQuestion> findByQuizIdOrderByQuestionOrder(String quizId);
}
