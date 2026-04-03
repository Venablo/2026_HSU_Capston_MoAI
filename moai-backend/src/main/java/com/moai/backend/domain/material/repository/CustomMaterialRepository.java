package com.moai.backend.domain.material.repository;

import com.moai.backend.domain.material.entity.CustomMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomMaterialRepository extends JpaRepository<CustomMaterial, String> {

    List<CustomMaterial> findByRoomIdOrderByCreatedAtDesc(String roomId);
}
