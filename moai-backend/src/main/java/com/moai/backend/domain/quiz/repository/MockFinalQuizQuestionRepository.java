package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.MockFinalQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockFinalQuizQuestionRepository extends JpaRepository<MockFinalQuizQuestion, String> {

    List<MockFinalQuizQuestion> findByMockQuizIdOrderByQuestionOrder(String mockQuizId);
}
