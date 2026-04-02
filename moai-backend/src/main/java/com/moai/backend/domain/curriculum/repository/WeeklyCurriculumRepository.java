package com.moai.backend.domain.curriculum.repository;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeeklyCurriculumRepository extends JpaRepository<WeeklyCurriculum, String> {

    List<WeeklyCurriculum> findByRoomIdOrderByWeekNumber(String roomId);

    Optional<WeeklyCurriculum> findByIdAndRoomId(String id, String roomId);
}
