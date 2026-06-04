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
}
