package com.moai.backend.domain.chat.repository;

import com.moai.backend.domain.chat.entity.StudyMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMessageRepository extends JpaRepository<StudyMessage, String> {

    Page<StudyMessage> findByGroupIdOrderBySentAtDesc(String groupId, Pageable pageable);
}
