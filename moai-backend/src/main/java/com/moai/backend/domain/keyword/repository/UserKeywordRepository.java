package com.moai.backend.domain.keyword.repository;

import com.moai.backend.domain.keyword.entity.UserKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserKeywordRepository extends JpaRepository<UserKeyword, String> {

    Optional<UserKeyword> findByUserIdAndRoomIdAndKeyword(String userId, String roomId, String keyword);

    Optional<UserKeyword> findByUserIdAndRoomIdAndKeywordAndKeywordType(
            String userId, String roomId, String keyword, String keywordType);

    List<UserKeyword> findByUserIdAndRoomId(String userId, String roomId);

    List<UserKeyword> findByUserIdAndRoomIdAndCurriculumId(String userId, String roomId, String curriculumId);

    @Modifying
    @Query("DELETE FROM UserKeyword uk WHERE uk.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    List<UserKeyword> findByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqualOrderByWeaknessCountDesc(
            String userId, String curriculumId, String keywordType, Short weaknessCount);

    List<UserKeyword> findByKeywordAndKeywordTypeAndUserIdNot(
            String keyword, String keywordType, String userId);

    List<UserKeyword> findByUserIdAndKeywordTypeAndCreatedAtAfter(
            String userId, String keywordType, LocalDateTime createdAtAfter);
}
