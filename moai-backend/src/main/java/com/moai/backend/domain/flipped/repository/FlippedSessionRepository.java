package com.moai.backend.domain.flipped.repository;

import com.moai.backend.domain.flipped.entity.FlippedSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlippedSessionRepository extends JpaRepository<FlippedSession, String> {

    Optional<FlippedSession> findBySessionId(String sessionId);

    List<FlippedSession> findByUserIdAndRoomId(String userId, String roomId);
}
