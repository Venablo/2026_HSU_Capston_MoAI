package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.MockFinalQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockFinalQuizRepository extends JpaRepository<MockFinalQuiz, String> {

    Optional<MockFinalQuiz> findBySubjectAndWeekNumber(String subject, Short weekNumber);
}
