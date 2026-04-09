package com.moai.backend.domain.flipped.repository;

import com.moai.backend.domain.flipped.entity.AiInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, String> {

    List<AiInteraction> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
