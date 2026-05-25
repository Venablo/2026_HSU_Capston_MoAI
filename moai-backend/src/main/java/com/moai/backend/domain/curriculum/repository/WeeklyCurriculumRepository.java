package com.moai.backend.domain.curriculum.repository;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeeklyCurriculumRepository extends JpaRepository<WeeklyCurriculum, String> {

    List<WeeklyCurriculum> findByRoomIdOrderByWeekNumber(String roomId);

    @Query("SELECT wc.id FROM WeeklyCurriculum wc WHERE wc.room.id = :roomId ORDER BY wc.weekNumber")
    List<String> findIdsByRoomIdOrderByWeekNumber(@Param("roomId") String roomId);

    Optional<WeeklyCurriculum> findByIdAndRoomId(String id, String roomId);

    Optional<WeeklyCurriculum> findByRoomIdAndWeekNumber(String roomId, Short weekNumber);

    @Modifying
    @Query("DELETE FROM WeeklyCurriculum wc WHERE wc.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    // 시연용 cleanup: 해당 사용자의 모든 학습실 하위 주차 일괄 삭제
    @Modifying
    @Query("DELETE FROM WeeklyCurriculum wc WHERE wc.room.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
