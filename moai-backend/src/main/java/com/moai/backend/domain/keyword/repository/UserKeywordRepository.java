package com.moai.backend.domain.keyword.repository;

import com.moai.backend.domain.keyword.entity.UserKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserKeywordRepository extends JpaRepository<UserKeyword, String> {

    Optional<UserKeyword> findByUserIdAndRoomIdAndKeyword(String userId, String roomId, String keyword);
}
