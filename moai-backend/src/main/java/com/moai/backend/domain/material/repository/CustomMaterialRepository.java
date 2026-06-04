package com.moai.backend.domain.material.repository;

import com.moai.backend.domain.material.entity.CustomMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomMaterialRepository extends JpaRepository<CustomMaterial, String> {

    List<CustomMaterial> findByRoomIdOrderByCreatedAtDesc(String roomId);

    @Modifying
    @Query("delete from CustomMaterial m where m.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
