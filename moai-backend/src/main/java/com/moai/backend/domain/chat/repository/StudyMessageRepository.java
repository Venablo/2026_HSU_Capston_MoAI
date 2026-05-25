package com.moai.backend.domain.chat.repository;

import com.moai.backend.domain.chat.entity.StudyMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudyMessageRepository extends JpaRepository<StudyMessage, String> {

    Page<StudyMessage> findByGroupIdOrderBySentAtDesc(String groupId, Pageable pageable);

    // 시연용 cleanup
    @Modifying
    @Query("DELETE FROM StudyMessage sm WHERE sm.group.id IN :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") List<String> groupIds);
}
