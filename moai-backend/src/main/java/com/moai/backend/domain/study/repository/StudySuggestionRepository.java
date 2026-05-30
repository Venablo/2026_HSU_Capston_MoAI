package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudySuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudySuggestionRepository extends JpaRepository<StudySuggestion, String> {

    List<StudySuggestion> findBySuggestedToIdAndStatus(String userId, String status);

    List<StudySuggestion> findByGroupId(String groupId);

    Optional<StudySuggestion> findByIdAndSuggestedToId(String id, String userId);

    Optional<StudySuggestion> findByGroupIdAndSuggestedToId(String groupId, String userId);

    @Query("SELECT COUNT(s) > 0 FROM StudySuggestion s WHERE s.suggestedTo.id = :userId " +
            "AND s.group.id IN (SELECT s2.group.id FROM StudySuggestion s2 WHERE s2.suggestedTo.id = :partnerId) " +
            "AND s.group.status IN ('pending_acceptance')")
    boolean existsPendingBetween(@Param("userId") String userId, @Param("partnerId") String partnerId);

    @Query("SELECT COUNT(s) > 0 FROM StudySuggestion s " +
            "WHERE s.suggestedTo.id = :userId " +
            "AND s.group.id IN (SELECT s2.group.id FROM StudySuggestion s2 WHERE s2.suggestedTo.id = :partnerId) " +
            "AND s.group.status = 'disbanded' " +
            "AND s.group.disbandedAt > :oneDayAgo")
    boolean existsRecentlyRejectedBetween(
            @Param("userId") String userId,
            @Param("partnerId") String partnerId,
            @Param("oneDayAgo") LocalDateTime oneDayAgo);

    // 학습실 삭제 cleanup: 이 room 으로 만들어진 suggestion 이 속한 그룹 ID 목록.
    // 그룹 단위로 해체해야 양쪽 멤버의 suggestion(다른 room 참조)까지 함께 정리된다.
    @Query("SELECT DISTINCT s.group.id FROM StudySuggestion s WHERE s.room.id = :roomId")
    List<String> findGroupIdsByRoomId(@Param("roomId") String roomId);

    @Modifying
    @Query("DELETE FROM StudySuggestion s WHERE s.group.id IN :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") List<String> groupIds);
}
