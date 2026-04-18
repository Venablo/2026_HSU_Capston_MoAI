package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, String> {

    List<StudyGroup> findByStatusAndExpiresAtBefore(String status, LocalDateTime now);
}
