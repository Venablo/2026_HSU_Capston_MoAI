package com.moai.backend.domain.users.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.moai.backend.global.common.BaseTimeEntity;
import com.moai.backend.global.common.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_nickname", columnList = "nickname"),
        @Index(name = "idx_login_id", columnList = "login_id")
})
public class User extends BaseTimeEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Convert(converter = StringListConverter.class)
    @Column(name = "interest_keywords", columnDefinition = "json")
    private List<String> interestKeywords;

    @Column(name = "study_suggestion_enabled", nullable = false)
    private Boolean studySuggestionEnabled;

    @Column(name = "theme_preference", nullable = false, length = 10)
    private String themePreference;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    @Builder
    public User(String loginId, String passwordHash, String name, String nickname,
                String email, LocalDate birthDate, List<String> interestKeywords) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.nickname = nickname;
        this.email = email;
        this.birthDate = birthDate;
        this.interestKeywords = interestKeywords;
        this.status = "active";
        this.studySuggestionEnabled = true;
        this.themePreference = "light";
    }

    // 회원 탈퇴 (논리 삭제)
    public void deactivate() {
        this.status = "inactive";
    }

    // 관심 키워드 수정
    public void updateInterestKeywords(List<String> interestKeywords) {
        this.interestKeywords = interestKeywords;
    }

    // null이 아닌 필드만 업데이트하는 부분 수정 메서드
    public void updateProfile(String nickname, String profileImageUrl, String themePreference) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
        if (themePreference != null) {
            this.themePreference = themePreference;
        }
    }
}
