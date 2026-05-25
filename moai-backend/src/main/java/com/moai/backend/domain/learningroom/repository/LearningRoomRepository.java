package com.moai.backend.domain.learningroom.repository;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningRoomRepository extends JpaRepository<LearningRoom, String> {

    List<LearningRoom> findByUserId(String userId);

    Optional<LearningRoom> findByIdAndUserId(String id, String userId);

    List<LearningRoom> findByUserIdOrderByCreatedAtDesc(String userId);

    // 시연용 cleanup: 해당 사용자의 모든 학습실 일괄 삭제
    @Modifying
    @Query("DELETE FROM LearningRoom lr WHERE lr.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
