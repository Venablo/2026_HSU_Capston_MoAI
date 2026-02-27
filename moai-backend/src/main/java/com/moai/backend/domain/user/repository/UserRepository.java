package com.moai.backend.domain.user.repository;

import com.moai.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


// User 엔티티에 대한 데이터베이스 액세스 처리를 담당하는 리포지토리
// JpaRepository를 상속받아 기본적인 CRUD 기능을 제공
// <User, Long> -> User 엔티티를 관리하고, Id는 Long 타입
// 메서드 이름 기반 쿼리(Method Name Query) 기능을 활용

public interface UserRepository extends JpaRepository<User, Long> {
    // 이메일로 사용자를 찾는 기능을 추가
    Optional<User> findByEmail(String email); // 결과가 없을 상황(null)을 대비해 Optional에 담아서 반환

    // 닉네임으로 사용자를 찾는 기능을 추가
    Optional<User> findByNickname(String nickname); // 결과가 없을 상황(null)을 대비해 Optional에 담아서 반환
}