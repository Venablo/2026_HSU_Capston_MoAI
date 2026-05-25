package com.moai.backend.domain.demo.repository;

import com.moai.backend.domain.demo.entity.MockCurriculumTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockCurriculumTemplateRepository extends JpaRepository<MockCurriculumTemplate, Long> {

    List<MockCurriculumTemplate> findAllByOrderByWeekNumberAsc();
}
