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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

        // Step 5: LLM 호출로 최적 멘토 선택
        LlmMatchingResult llmResult = null;
        try {
            llmResult = callLlmForMatching(currentUser, weaknesses, candidates);

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

    private LlmMatchingResult callLlmForMatching(User targetUser, List<UserKeyword> weaknesses, List<Candidate> candidates) {
        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 AI 매칭 전문가입니다.

                학습자(멘티)의 약점 키워드와 후보 사용자 풀의 강점/약점을 분석하여 최적의 멘토를 매칭하세요.

                ■ 출력: 순수 JSON
                {
                  "target_user": {"user_id": "...", "nickname": "..."},
                  "matches": [
                    {
                      "rank": 1,
                      "user_id": "매칭된 유저 ID",
                      "nickname": "닉네임",
                      "match_score": 0~100,
                      "match_reasons": ["매칭 이유1 (구체적)", "이유2", "이유3", "이유4"],
                      "complementary_keywords": [
                        {"keyword": "키워드", "target_weakness": true, "candidate_strength": true, "note": "왜 잘 맞는지"}
                      ],
                      "recommended_tag": "추천 태그 (예: #고립성_해결사, #SQL_마스터)",
                      "collaboration_tip": "이 멘토와 함께하면 좋은 학습 방법 제안",
                      "first_session_plan": ["...", "...", "...", "..."],
                      "weekly_goal": "...",
                      "caution_points": ["...", "...", "..."],
                      "recommended_resources": ["...", "...", "..."]
                    }
                  ],
                  "unmatched_keywords": ["매칭되지 못한 약점 키워드"],
                  "matching_summary": "매칭 결과 요약 (3문장 이상)",
                  "study_group_suggestion": "스터디 그룹 구성 제안"
                }

                ■ 규칙:
                1. 멘티의 약점이 멘토의 강점인 경우 우선 매칭 (핵심 기준)
                2. 약점끼리 매칭되는 경우 제외 (잘못된 매칭)
                3. match_score 산정: 키워드 겹침 수 × 40 + 다양성 보너스 + 경험도 반영 (최대 100)
                4. 동점자 처리: 더 많은 키워드를 커버하는 후보 우선
                5. 매칭 불가 시 unmatched_keywords에 명시하고 matches는 빈 배열로 반환
                6. recommended_tag는 해시태그 형태 (#키워드_역할)
                7. matches는 match_score 내림차순(rank 1이 최고)으로 정렬
                """;

        StringBuilder targetKeywords = new StringBuilder("[");
        for (int i = 0; i < weaknesses.size(); i++) {
            UserKeyword w = weaknesses.get(i);
            if (i > 0) targetKeywords.append(",");
            targetKeywords.append(String.format(
                    "{\"keyword\":%s,\"keyword_type\":\"weakness\",\"weakness_count\":%d}",
                    jsonStr(w.getKeyword()), w.getWeaknessCount()));
        }
        targetKeywords.append("]");

        StringBuilder pool = new StringBuilder("[");
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (i > 0) pool.append(",");
            StringBuilder kw = new StringBuilder("[");
            List<String> strengths = c.strengths();
            for (int j = 0; j < strengths.size(); j++) {
                if (j > 0) kw.append(",");
                kw.append(String.format(
                        "{\"keyword\":%s,\"keyword_type\":\"strength\",\"weakness_count\":0}",
                        jsonStr(strengths.get(j))));
            }
            kw.append("]");
            pool.append(String.format(
                    "{\"user_id\":%s,\"nickname\":%s,\"keywords\":%s}",
                    jsonStr(c.user().getId()), jsonStr(c.user().getNickname()), kw));
        }
        pool.append("]");

        String userMessage = String.format(
                "{\"target_user\":{\"user_id\":%s,\"nickname\":%s,\"keywords\":%s},\"candidate_pool\":%s}",
                jsonStr(targetUser.getId()), jsonStr(targetUser.getNickname()), targetKeywords, pool);

        LlmRichMatchingResponse rich = llmService.callJson(
                new LlmRequestDto(systemPrompt, userMessage), LlmRichMatchingResponse.class);

        return mapRichMatchingToResult(rich, candidates);
    }

    private LlmMatchingResult mapRichMatchingToResult(LlmRichMatchingResponse rich, List<Candidate> candidates) {
        if (rich == null || rich.getMatches() == null || rich.getMatches().isEmpty()) {
            return new LlmMatchingResult(-1, BigDecimal.ZERO, "");
        }

        LlmRichMatchingResponse.Match top = rich.getMatches().stream()
                .min(Comparator.comparingInt(m -> m.getRank() == null ? Integer.MAX_VALUE : m.getRank()))
                .orElse(rich.getMatches().get(0));

        int selectedIndex = -1;
        if (top.getUserId() != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (top.getUserId().equals(candidates.get(i).user().getId())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex < 0 && top.getNickname() != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (top.getNickname().equals(candidates.get(i).user().getNickname())) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        int score100 = top.getMatchScore() == null ? 60 : Math.max(0, Math.min(100, top.getMatchScore()));
        BigDecimal matchScore = BigDecimal.valueOf(score100)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);

        StringBuilder reason = new StringBuilder();
        if (top.getRecommendedTag() != null && !top.getRecommendedTag().isBlank()) {
            reason.append(top.getRecommendedTag()).append(" — ");
        }
        if (top.getMatchReasons() != null && !top.getMatchReasons().isEmpty()) {
            reason.append(String.join(" ", top.getMatchReasons()));
        }
        if (top.getComplementaryKeywords() != null && !top.getComplementaryKeywords().isEmpty()) {
            String keywords = top.getComplementaryKeywords().stream()
                    .map(LlmRichMatchingResponse.Complementary::getKeyword)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (!keywords.isBlank()) {
                reason.append(" 보완 키워드: ").append(keywords).append(".");
            }
        }
        if (top.getCollaborationTip() != null && !top.getCollaborationTip().isBlank()) {
            reason.append(" ").append(top.getCollaborationTip());
        }
        String matchReason = reason.toString().trim();
        if (matchReason.isEmpty()) {
            matchReason = "약점 키워드와 후보의 강점 키워드가 강하게 맞아 선정되었습니다.";
        }

        return new LlmMatchingResult(selectedIndex, matchScore, matchReason);
    }

    private String jsonStr(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
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

    @Getter
    @NoArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class LlmRichMatchingResponse {

        @JsonProperty("target_user")
        private TargetUser targetUser;

        private List<Match> matches;

        @JsonProperty("unmatched_keywords")
        private List<String> unmatchedKeywords;

        @JsonProperty("matching_summary")
        private String matchingSummary;

        @JsonProperty("study_group_suggestion")
        private String studyGroupSuggestion;

        @Getter
        @NoArgsConstructor
        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        static class TargetUser {
            @JsonProperty("user_id")
            private String userId;
            private String nickname;
        }

        @Getter
        @NoArgsConstructor
        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        static class Match {
            private Integer rank;

            @JsonProperty("user_id")
            private String userId;

            private String nickname;

            @JsonProperty("match_score")
            private Integer matchScore;

            @JsonProperty("match_reasons")
            private List<String> matchReasons;

            @JsonProperty("complementary_keywords")
            private List<Complementary> complementaryKeywords;

            @JsonProperty("recommended_tag")
            private String recommendedTag;

            @JsonProperty("collaboration_tip")
            private String collaborationTip;

            @JsonProperty("first_session_plan")
            private List<String> firstSessionPlan;

            @JsonProperty("weekly_goal")
            private String weeklyGoal;

            @JsonProperty("caution_points")
            private List<String> cautionPoints;

            @JsonProperty("recommended_resources")
            private List<String> recommendedResources;
        }

        @Getter
        @NoArgsConstructor
        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        static class Complementary {
            private String keyword;
            private String note;

            @JsonProperty("target_weakness")
            private Boolean targetWeakness;

            @JsonProperty("candidate_strength")
            private Boolean candidateStrength;
        }
    }
}
