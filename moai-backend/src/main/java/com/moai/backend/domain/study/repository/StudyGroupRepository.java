package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, String> {
}
