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

    // 약점/강점 upsert 시 현재 주차(curriculum) 안에서만 같은 키워드 레코드를 찾기 위한 조회.
    // week 1 의 약점 "X" 와 week 3 의 약점 "X" 는 서로 다른 레코드로 관리된다.
    Optional<UserKeyword> findByUserIdAndCurriculumIdAndKeywordAndKeywordType(
            String userId, String curriculumId, String keyword, String keywordType);

    List<UserKeyword> findByUserIdAndRoomId(String userId, String roomId);

    List<UserKeyword> findByUserIdAndRoomIdAndCurriculumId(String userId, String roomId, String curriculumId);

    @Modifying
    @Query("DELETE FROM UserKeyword uk WHERE uk.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    // 시연용 cleanup. 학생 C strength 시드는 user_id 가 다르므로 영향 없음.
    @Modifying
    @Query("DELETE FROM UserKeyword uk WHERE uk.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);

    List<UserKeyword> findByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqualOrderByWeaknessCountDesc(
            String userId, String curriculumId, String keywordType, Short weaknessCount);

    // 매칭 가능한 약점 키워드 존재 여부 조회 (미해소 + 누적 카운트 임계값 이상). 첫 매칭 즉시 종료되도록 exists 사용.
    boolean existsByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqual(
            String userId, String curriculumId, String keywordType, Short weaknessCount);

    List<UserKeyword> findByKeywordAndKeywordTypeAndUserIdNot(
            String keyword, String keywordType, String userId);

    List<UserKeyword> findByUserIdAndKeywordTypeAndCreatedAtAfter(
            String userId, String keywordType, LocalDateTime createdAtAfter);
}
