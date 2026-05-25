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

    // 시연용 cleanup: 해당 사용자가 받은 모든 제안의 그룹 ID 목록.
    // pending 인 채로 수락 전인 그룹도 포함하기 위해 StudyMember 경로와 별도로 수집한다.
    @Query("SELECT s.group.id FROM StudySuggestion s WHERE s.suggestedTo.id = :userId")
    List<String> findGroupIdsBySuggestedToId(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM StudySuggestion s WHERE s.group.id IN :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") List<String> groupIds);
}
