package com.moai.backend.domain.demo.service;

import com.moai.backend.domain.chat.repository.StudyMessageRepository;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.eventlog.repository.LearningEventLogRepository;
import com.moai.backend.domain.flipped.repository.AiInteractionRepository;
import com.moai.backend.domain.flipped.repository.FlippedSessionRepository;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.material.repository.CustomMaterialRepository;
import com.moai.backend.domain.notification.repository.NotificationRepository;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizReportRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.study.repository.StudyGroupRepository;
import com.moai.backend.domain.study.repository.StudyMemberRepository;
import com.moai.backend.domain.study.repository.StudySuggestionRepository;
import com.moai.backend.domain.transcript.repository.VideoTranscriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 시연용 데모 데이터 초기화 서비스.
 *
 * 원칙
 *   1. 인자로 받은 userId 만 정리 (하드코딩 금지).
 *   2. users 행은 절대 삭제하지 않는다.
 *   3. 학생 C strength 키워드 시드는 다른 user_id 라 자동으로 보존된다.
 *   4. mock_curriculum_templates / mock_transcript_templates 는 영구 보관 — 손대지 않는다.
 *
 * 삭제 전략
 *   FK 자식 → 부모 역순. JPQL FK 체인을 깊게 타서 중간 ID 리스트 fetch 없이 직접 DELETE.
 *   ex) DELETE FROM VideoTranscript vt WHERE vt.curriculum.room.user.id = :userId
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DemoResetService {

    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizReportRepository quizReportRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRepository quizRepository;
    private final VideoTranscriptRepository videoTranscriptRepository;
    private final LearningEventLogRepository learningEventLogRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final FlippedSessionRepository flippedSessionRepository;
    private final UserKeywordRepository userKeywordRepository;
    private final CustomMaterialRepository customMaterialRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final NotificationRepository notificationRepository;
    private final StudyMessageRepository studyMessageRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudySuggestionRepository studySuggestionRepository;
    private final StudyGroupRepository studyGroupRepository;

    @Value("${moai.demo.cleanup-login-ids:}")
    private String cleanupLoginIdsRaw;

    /**
     * 주어진 login_id 가 시연 계정 화이트리스트에 포함되는지 확인.
     * application.yaml moai.demo.cleanup-login-ids 가 빈 값이면 항상 false (운영 환경 안전망).
     */
    public boolean isDemoAccount(String loginId) {
        if (loginId == null || cleanupLoginIdsRaw == null || cleanupLoginIdsRaw.isBlank()) {
            return false;
        }
        Set<String> allowed = Arrays.stream(cleanupLoginIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return allowed.contains(loginId);
    }

    /**
     * 김코딩 등 시연 계정의 모든 자취를 삭제한다.
     * users 행 자체는 보존하므로 로그아웃 후 재로그인이 가능하다.
     */
    public void cleanupDemoData(String userId) {
        log.info("[demo] cleanupDemoData 시작: userId={}", userId);

        // 1. 학습 활동 자식 테이블 (FK 자식부터)
        quizAttemptRepository.deleteByUserId(userId);
        quizReportRepository.deleteByUserId(userId);
        quizQuestionRepository.deleteByUserId(userId);
        quizRepository.deleteByUserId(userId);
        videoTranscriptRepository.deleteByUserId(userId);
        learningEventLogRepository.deleteByUserId(userId);
        aiInteractionRepository.deleteByUserId(userId);
        flippedSessionRepository.deleteByUserId(userId);
        customMaterialRepository.deleteByUserId(userId);
        userKeywordRepository.deleteByUserId(userId);

        // 2. 스터디 정리 — weekly_curriculums 삭제보다 먼저 처리해야 한다.
        //    study_suggestions.curriculum_id FK 가 살아있으면 weekly_curriculums 삭제가 거부됨.
        //    - 김코딩이 멤버였던 그룹 (양측 수락 완료)
        //    - 김코딩이 받은 pending/rejected suggestion 의 그룹 (수락 전이라 member 에 없음)
        //    두 경로 합집합으로 group_id 모음.
        List<String> memberGroupIds = studyMemberRepository.findGroupIdsByUserId(userId);
        List<String> suggestedGroupIds = studySuggestionRepository.findGroupIdsBySuggestedToId(userId);

        List<String> groupIds = Stream.concat(memberGroupIds.stream(), suggestedGroupIds.stream())
                .distinct()
                .toList();

        if (!groupIds.isEmpty()) {
            studyMessageRepository.deleteByGroupIdIn(groupIds);
            studyMemberRepository.deleteByGroupIdIn(groupIds);
            studySuggestionRepository.deleteByGroupIdIn(groupIds);
            studyGroupRepository.deleteAllByIdInBatch(groupIds);
        }

        // 3. 주차 커리큘럼 → 학습실
        weeklyCurriculumRepository.deleteByUserId(userId);
        learningRoomRepository.deleteByUserId(userId);

        // 4. 알림
        notificationRepository.deleteByUserId(userId);

        log.info("[demo] cleanupDemoData 완료: userId={}, 정리된 스터디 그룹 수={}", userId, groupIds.size());
    }
}
