package com.moai.backend.domain.demo.repository;

import com.moai.backend.domain.demo.entity.MockTranscriptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockTranscriptTemplateRepository extends JpaRepository<MockTranscriptTemplate, Long> {

    List<MockTranscriptTemplate> findByTemplateIdOrderByChunkIndexAsc(Long templateId);
}
