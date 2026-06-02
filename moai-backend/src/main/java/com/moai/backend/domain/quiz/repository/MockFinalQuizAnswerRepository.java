package com.moai.backend.domain.quiz.repository;

import com.moai.backend.domain.quiz.entity.MockFinalQuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockFinalQuizAnswerRepository extends JpaRepository<MockFinalQuizAnswer, String> {

    List<MockFinalQuizAnswer> findByMockQuizIdOrderByQuestionOrder(String mockQuizId);
}
