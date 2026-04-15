package com.moai.backend.domain.study.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.notification.dto.SseStudyMatchEvent;
import com.moai.backend.domain.notification.entity.Notification;
import com.moai.backend.domain.notification.repository.NotificationRepository;
import com.moai.backend.domain.notification.service.NotificationService;
import com.moai.backend.domain.study.dto.LlmMatchingResult;
import com.moai.backend.domain.study.entity.StudyGroup;
import com.moai.backend.domain.study.entity.StudySuggestion;
import com.moai.backend.domain.study.repository.StudyGroupRepository;
import com.moai.backend.domain.study.repository.StudySuggestionRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingEngineService {

    private final UserKeywordRepository userKeywordRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final StudySuggestionRepository studySuggestionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final LlmService llmService;

    private static final int MAX_CANDIDATES = 5;

    @Async
    @Transactional
    public void tryMatch(User currentUser, LearningRoom room, WeeklyCurriculum curriculum) {
        try {
            doMatch(currentUser, room, curriculum);
        } catch (Exception e) {
            log.error("매칭 엔진 실행 실패 - userId: {}, roomId: {}", currentUser.getId(), room.getId(), e);
        }
    }

    private record Candidate(User user, String matchKeyword, short weaknessCount, List<String> strengths) {}

    private void doMatch(User currentUser, LearningRoom room, WeeklyCurriculum curriculum) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        // Step 1: 현재 사용자의 약점 키워드 조회
        List<UserKeyword> weaknesses = userKeywordRepository
                .findByUserIdAndCurriculumIdAndKeywordTypeAndIsResolvedFalseAndWeaknessCountGreaterThanEqualOrderByWeaknessCountDesc(
                        currentUser.getId(), curriculum.getId(), "weakness", (short) 3);

        // Step 2: 후보자 수집 (중복 제거, 최대 MAX_CANDIDATES명)
        Map<String, Candidate> candidateMap = new LinkedHashMap<>();

        for (UserKeyword weakness : weaknesses) {
            if (candidateMap.size() >= MAX_CANDIDATES) break;

            List<UserKeyword> strengthHolders = userKeywordRepository
                    .findByKeywordAndKeywordTypeAndUserIdNot(
                            weakness.getKeyword(), "strength", currentUser.getId());

            for (UserKeyword strengthHolder : strengthHolders) {
                if (candidateMap.size() >= MAX_CANDIDATES) break;

                User candidate = strengthHolder.getUser();
                if (candidateMap.containsKey(candidate.getId())) continue;
                if (!Boolean.TRUE.equals(candidate.getStudySuggestionEnabled())) continue;
                if (!Boolean.TRUE.equals(redisTemplate.hasKey("RT:" + candidate.getEmail()))) continue;
                if (studySuggestionRepository.existsActiveOrPendingBetween(
                        currentUser.getId(), candidate.getId())) continue;

                // Step 3: 후보자의 최근 7일 강점 키워드 전체 조회
                List<String> strengths = userKeywordRepository
                        .findByUserIdAndKeywordTypeAndCreatedAtAfter(candidate.getId(), "strength", sevenDaysAgo)
                        .stream()
                        .map(UserKeyword::getKeyword)
                        .distinct()
                        .toList();

                candidateMap.put(candidate.getId(),
                        new Candidate(candidate, weakness.getKeyword(), weakness.getWeaknessCount(), strengths));
            }
        }

        // Step 4: 후보자가 없으면 종료
        if (candidateMap.isEmpty()) return;

        List<Candidate> candidates = new ArrayList<>(candidateMap.values());

        // 약점 키워드 목록 (weakness_count 포함)
        String weaknessInfo = weaknesses.stream()
                .map(w -> String.format("%s(%d회)", w.getKeyword(), w.getWeaknessCount()))
                .distinct()
                .collect(Collectors.joining(", "));

        // Step 5: LLM 호출로 최적 멘토 선택
        LlmMatchingResult llmResult = null;
        try {
            llmResult = callLlmForMatching(weaknessInfo, candidates);

            if (llmResult.getSelectedIndex() < 0 || llmResult.getSelectedIndex() >= candidates.size()) {
                log.warn("LLM이 유효하지 않은 인덱스 반환: {}", llmResult.getSelectedIndex());
                llmResult = null;
            }
        } catch (Exception e) {
            log.warn("LLM 매칭 호출 실패, 첫 번째 후보로 폴백: {}", e.getMessage());
        }

        // Step 6: 매칭 결과 생성
        Candidate selected;
        BigDecimal matchScore;
        String matchReason;

        if (llmResult != null) {
            selected = candidates.get(llmResult.getSelectedIndex());
            matchScore = llmResult.getMatchScore();
            matchReason = llmResult.getMatchReason();
        } else {
            selected = candidates.get(0);
            matchScore = BigDecimal.valueOf(Math.min(0.6 + selected.weaknessCount() * 0.05, 0.990))
                    .setScale(3, RoundingMode.HALF_UP);
            matchReason = "회원님의 학습 데이터를 분석하여, 취약점을 완벽히 해결해 줄 1:1 멘토를 매칭했습니다.";
        }

        createMatchResult(currentUser, selected.user(), selected.matchKeyword(),
                matchScore, matchReason, room);
    }

    private LlmMatchingResult callLlmForMatching(String weaknessInfo, List<Candidate> candidates) {
        String systemPrompt = """
                당신은 AI 학습 멘토 매칭 전문가입니다.
                학생의 약점 키워드와 후보 멘토들의 강점 키워드를 분석하여 가장 적합한 멘토 1명을 선택해주세요.
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.

                {
                  "selectedIndex": 0~N (선택한 후보 인덱스),
                  "matchScore": 0.000~1.000 (적합도 점수),
                  "matchReason": "매칭 이유 설명 (한국어, 2~3문장)"
                }
                """;

        StringBuilder userMessage = new StringBuilder();
        userMessage.append(String.format("학생의 약점 키워드: [%s]\n\n후보 멘토 목록:\n", weaknessInfo));

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            String strengthList = String.join(", ", c.strengths());
            userMessage.append(String.format("[%d] 닉네임: %s — 강점 키워드: [%s]\n",
                    i, c.user().getNickname(), strengthList));
        }

        userMessage.append("\n가장 적합한 멘토 1명을 선택하고, matchScore와 matchReason을 생성해주세요.");

        return llmService.callJson(new LlmRequestDto(systemPrompt, userMessage.toString()), LlmMatchingResult.class);
    }

    private void createMatchResult(User mentee, User mentor, String matchKeyword,
                                   BigDecimal matchScore, String matchReason, LearningRoom room) {
        StudyGroup group = StudyGroup.builder()
                .type("mentor_mentee")
                .subject(room.getSubject())
                .matchKeyword(matchKeyword)
                .matchReason(matchReason)
                .matchScore(matchScore)
                .status("pending_acceptance")
                .build();
        studyGroupRepository.save(group);

        StudySuggestion menteeSuggestion = StudySuggestion.builder()
                .group(group)
                .suggestedTo(mentee)
                .suggestedRole("mentee")
                .build();
        studySuggestionRepository.save(menteeSuggestion);

        StudySuggestion mentorSuggestion = StudySuggestion.builder()
                .group(group)
                .suggestedTo(mentor)
                .suggestedRole("mentor")
                .build();
        studySuggestionRepository.save(mentorSuggestion);

        String menteeMessage = "회원님의 약점을 완벽히 보완해줄 멘토를 찾았습니다!";
        String mentorMessage = "회원님의 강점을 필요로 하는 멘티를 찾았습니다!";

        Notification menteeNotification = Notification.builder()
                .user(mentee)
                .type("study_match")
                .message(menteeMessage)
                .referenceId(menteeSuggestion.getId())
                .build();
        notificationRepository.save(menteeNotification);

        Notification mentorNotification = Notification.builder()
                .user(mentor)
                .type("study_match")
                .message(mentorMessage)
                .referenceId(mentorSuggestion.getId())
                .build();
        notificationRepository.save(mentorNotification);

        SseStudyMatchEvent menteeEvent = new SseStudyMatchEvent(
                "study_match", menteeMessage, menteeSuggestion.getId(),
                new SseStudyMatchEvent.PartnerInfo(mentor.getNickname(), "mentor"),
                matchScore, matchKeyword);
        notificationService.pushSse(mentee.getId(), menteeEvent);

        SseStudyMatchEvent mentorEvent = new SseStudyMatchEvent(
                "study_match", mentorMessage, mentorSuggestion.getId(),
                new SseStudyMatchEvent.PartnerInfo(mentee.getNickname(), "mentee"),
                matchScore, matchKeyword);
        notificationService.pushSse(mentor.getId(), mentorEvent);

        log.info("매칭 성공 - mentee: {}, mentor: {}, keyword: {}, score: {}",
                mentee.getId(), mentor.getId(), matchKeyword, matchScore);
    }
}
