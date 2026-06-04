package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudyMemberRepository extends JpaRepository<StudyMember, String> {

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    List<StudyMember> findByGroupId(String groupId);

    @Query("SELECT m FROM StudyMember m JOIN FETCH m.group g " +
            "WHERE m.user.id = :userId AND g.status = :status")
    List<StudyMember> findByUserIdAndGroupStatus(
            @Param("userId") String userId,
            @Param("status") String status);

    @Modifying
    @Query("DELETE FROM StudyMember m WHERE m.group.id IN :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") List<String> groupIds);
}
