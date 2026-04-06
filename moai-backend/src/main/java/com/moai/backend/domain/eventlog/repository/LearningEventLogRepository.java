package com.moai.backend.domain.eventlog.repository;

import com.moai.backend.domain.eventlog.entity.LearningEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningEventLogRepository extends JpaRepository<LearningEventLog, String> {
}
