package com.moai.backend.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 이 클래스를 상속받는 엔티티들이 아래 필드들을 DB 컬럼으로 인식.
@EntityListeners(AuditingEntityListener.class) // 엔티티가 변경될 때 시간을 자동으로 기록하는 감시자.
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false) // 최초 생성 시에만 값이 들어가고, 이후엔 수정 불가
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at") // 데이터가 수정될 때마다 자동으로 시간이 갱신됨
    private LocalDateTime updatedAt;
}