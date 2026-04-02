package com.moai.backend.domain.learningroom.repository;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningRoomRepository extends JpaRepository<LearningRoom, String> {

    List<LearningRoom> findByUserId(String userId);

    Optional<LearningRoom> findByIdAndUserId(String id, String userId);
}
