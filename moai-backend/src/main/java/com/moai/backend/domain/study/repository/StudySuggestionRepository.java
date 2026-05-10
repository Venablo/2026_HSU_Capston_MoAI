package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudySuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
