package com.moai.backend.domain.flipped.repository;

import com.moai.backend.domain.flipped.entity.FlippedSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FlippedSessionRepository extends JpaRepository<FlippedSession, String> {

    Optional<FlippedSession> findBySessionId(String sessionId);

    List<FlippedSession> findByUserIdAndRoomId(String userId, String roomId);

    Optional<FlippedSession> findByUserIdAndCurriculumId(String userId, String curriculumId);

    @Modifying
    @Query("DELETE FROM FlippedSession fs WHERE fs.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
