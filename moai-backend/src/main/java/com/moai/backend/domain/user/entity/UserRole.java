package com.moai.backend.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor // final이 붙은 필드를 인자로 받는 생성자를 자동으로 생성
public enum UserRole {

    STUDENT("ROLE_STUDENT", "학생"),
    ADMIN("ROLE_ADMIN", "관리자");

    private final String key;
    private final String title;
}
