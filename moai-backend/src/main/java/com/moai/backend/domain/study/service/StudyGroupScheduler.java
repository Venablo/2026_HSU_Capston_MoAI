package com.moai.backend.domain.study.service;

import com.moai.backend.domain.study.entity.StudyGroup;
import com.moai.backend.domain.study.repository.StudyGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyGroupScheduler {

    private final StudyGroupRepository studyGroupRepository;

    // 매시간 정각에 만료된 스터디 그룹을 completed 상태로 변경
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireStudyGroups() {
        List<StudyGroup> expiredGroups = studyGroupRepository
                .findByStatusAndExpiresAtBefore("active", LocalDateTime.now());

        for (StudyGroup group : expiredGroups) {
            group.complete();
        }

        if (!expiredGroups.isEmpty()) {
            log.info("만료된 스터디 그룹 {}개 상태를 completed로 변경했습니다.", expiredGroups.size());
        }
    }
}
