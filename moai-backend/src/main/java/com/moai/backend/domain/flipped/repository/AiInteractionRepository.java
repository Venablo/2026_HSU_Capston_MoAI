package com.moai.backend.domain.flipped.repository;

import com.moai.backend.domain.flipped.entity.AiInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, String> {

    List<AiInteraction> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Modifying
    @Query("DELETE FROM AiInteraction ai WHERE ai.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
