package com.moai.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// JPA는 프록시 생성을 위해 기본 생성자가 필수
// 외부에서 의미 없는 빈 객체(new User())를 생성하는 것은 막아 데이터 무결성을 유지
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_nickname", columnList = "nickname"),
        @Index(name = "idx_role", columnList = "user_role")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB의 AUTO_INCREMENT 기능을 활용하여 고유 번호(PK)를 자동으로 생성
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true) // 중복 가입을 방지하기 위해 unique 설정을 추가
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt로 암호화된 비밀번호가 저장

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING) // DB에는 'STUDENT'라는 문자열로 저장
    @Column(name = "user_role", nullable = false)
    private UserRole userRole = UserRole.STUDENT;

    @CreationTimestamp // INSERT 시 현재 시간 자동 입력
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp // UPDATE 시 현재 시간 자동 갱신
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // 빌더 패턴을 적용
    // 빌더는 객체를 만들 때 필드에 적힌 기본값을 무시하고, 일단 모든 필드를 null(혹은 기본값 0)로 비워둔 상태에서 시작
    @Builder
    public User(String email, String password, String nickname, UserRole userRole) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.userRole = (userRole != null) ? userRole : UserRole.STUDENT;
        this.isActive = true;
    }
}