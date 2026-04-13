package com.moai.backend.domain.study.repository;

import com.moai.backend.domain.study.entity.StudyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyMemberRepository extends JpaRepository<StudyMember, String> {

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    List<StudyMember> findByGroupId(String groupId);
}
