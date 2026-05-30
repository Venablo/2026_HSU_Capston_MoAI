package com.moai.backend.domain.curriculum.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.quiz.entity.Quiz;
import com.moai.backend.domain.quiz.entity.QuizOption;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import com.moai.backend.domain.quiz.repository.QuizQuestionRepository;
import com.moai.backend.domain.quiz.repository.QuizRepository;
import com.moai.backend.domain.transcript.entity.VideoTranscript;
import com.moai.backend.domain.transcript.repository.VideoTranscriptRepository;
import com.moai.backend.domain.notification.dto.SseSimpleEvent;
import com.moai.backend.domain.notification.service.NotificationService;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import com.moai.backend.global.material.MaterialContent;
import com.moai.backend.global.material.MaterialGeneratorService;
import com.moai.backend.global.s3.S3Service;
import com.moai.backend.global.subtitle.SubtitleRetryQueue;
import com.moai.backend.global.subtitle.SubtitleScraper;
import com.moai.backend.global.subtitle.dto.SubtitleChunk;
import com.moai.backend.global.subtitle.dto.SubtitleScrapeResult;
import com.moai.backend.global.subtitle.exception.SubtitleErrorCode;
import com.moai.backend.global.subtitle.exception.SubtitleScrapeException;
import com.moai.backend.global.youtube.YoutubeApiService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumEnrichmentService {

    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final VideoTranscriptRepository videoTranscriptRepository;
    private final LlmService llmService;
    private final SubtitleScraper subtitleScraper;
    private final MaterialGeneratorService materialGeneratorService;
    private final S3Service s3Service;
    private final YoutubeApiService youtubeApiService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final NotificationService notificationService;
    private final SubtitleRetryQueue subtitleRetryQueue;

    private static final Pattern BAD_VIDEO_PATTERN = Pattern.compile(
            "기출|문제|문제풀이|풀이|해설|모의고사|예상문제|암기법|벼락치기|합격후기|shorts|쇼츠",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern META_VIDEO_PATTERN = Pattern.compile(
            "공부법|학습법|독학|로드맵|커리큘럼 추천|채널 추천|시험 정보|응시자격|합격 전략|빠르게 요약|초단기|한방 정리|오리엔테이션",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONCEPT_VIDEO_PATTERN = Pattern.compile(
            "강의|개념|이론|원리|기초|입문|정리|문법|소프트웨어 설계|객체지향|데이터베이스|정규화|프로그래밍 언어|운영체제|네트워크|보안",
            Pattern.CASE_INSENSITIVE
    );
    private static final int LLM_VIDEO_CANDIDATE_LIMIT = 5;
    private static final int WEEK_VIDEO_MAX_SELECTION = 2;
    private static final int WEAKNESS_VIDEO_MAX_SELECTION = 1;

    /**
     * 재시도 큐에서 @Async/@Transactional 프록시를 거쳐 자기 자신을 호출하기 위한 self 참조.
     * 같은 빈에서 메서드를 직접 호출하면 트랜잭션 어드바이저를 우회하므로 @Lazy 자기 주입을 사용한다.
     */
    @Lazy
    @Autowired
    private CurriculumEnrichmentService self;

    private static final long SUBTITLE_RATE_LIMIT_RETRY_SEC = 60L;

    public record WeekEnrichmentContext(
            String curriculumId,
            String topic,
            String weeklySummary,
            List<String> learningObjectives,
            List<String> keyConcepts,
            List<String> focusQuestions,
            List<String> practiceKeywords,
            String youtubeSearchQuery,
            String subject,
            String level
    ) {}

    /**
     * 주차별 비동기 enrichment 파이프라인.
     * Step A → Step B → Step C -> Step D 순차 실행. 각 단계 실패 시 해당 주차를 스킵한다.
     *
     * 각 @Async 메서드는 별도 스레드에서 실행되므로 독립적인 트랜잭션 경계를 갖는다.
     * 단계별로 개별 저장하여, 이전 단계 결과는 다음 단계가 실패해도 유지된다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void enrichWeek(WeekEnrichmentContext context) {
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(context.curriculumId())
                .orElse(null);
        if (curriculum == null) {
            log.warn("enrichment 대상 커리큘럼을 찾을 수 없음: curriculumId={}", context.curriculumId());
            return;
        }

        log.info("[{}주차] enrichment 시작 — topic: {}", curriculum.getWeekNumber(), curriculum.getTopic());

        // Step A: YouTube 검색으로 후보 풀 구성 → LLM이 1~2개 영상 선택
        List<YoutubeApiService.VideoMeta> candidates = gatherCandidates(curriculum, context);
        Set<String> excludedVideoIds = new HashSet<>();
        List<String> llmSelectedIds = llmSelectVideoIds(curriculum, context, candidates);
        List<YoutubeApiService.VideoMeta> selectedVideos = pickVideosWithLlm(curriculum, context, candidates, llmSelectedIds, excludedVideoIds);

        if (selectedVideos.isEmpty()) {
            applyFallbackKeywordsIfMissing(curriculum, context);
            generateAndUploadMaterials(curriculum, context);
            log.warn("[{}주차] 영상 추천 실패 — 키워드/자료 fallback으로 enrichment 진행", curriculum.getWeekNumber());
            return;
        }

        // 선택된 영상들을 resources에 저장 (주력 영상 먼저)
        List<CurriculumResource> initialResources = selectedVideos.stream()
                .map(v -> toYoutubeResource(curriculum, v))
                .collect(Collectors.toCollection(ArrayList::new));
        curriculum.updateResources(mergeNormalYoutubeResources(curriculum.getResources(), initialResources));
        weeklyCurriculumRepository.save(curriculum);

        // Step B: YouTube 자막 스크래핑 (주력 영상 기준)
        // 학습실 생성 자체는 절대 실패시키지 않는다. 모든 분기는 이 try-catch 안에서 처리.
        YoutubeApiService.VideoMeta primaryVideo = selectedVideos.get(0);
        String videoId = primaryVideo.videoId();
        List<SubtitleChunk> chunks = new ArrayList<>();
        int transcriptCount = 0;
        try {
            SubtitleScrapeResult result = scrapeWithCache(curriculum, videoId);
            chunks.addAll(result.chunks());
            transcriptCount = saveTranscripts(curriculum, videoId, result.chunks());
            log.info("[{}주차] 자막 스크래핑 성공 — videoId={}, lang={}, source={}, chunks={}",
                    curriculum.getWeekNumber(), videoId, result.lang(), result.source(), transcriptCount);
        } catch (SubtitleScrapeException e) {
            SubtitleErrorCode code = e.getErrorCode();
            log.warn("[{}주차] 자막 스크래핑 실패 (videoId={}, code={}, detail={})",
                    curriculum.getWeekNumber(), videoId, code, e.getDetail());

            if (code == SubtitleErrorCode.NO_SUBTITLES_AVAILABLE) {
                excludedVideoIds.add(videoId);
                if (selectedVideos.size() > 1) excludedVideoIds.add(selectedVideos.get(1).videoId());
                SubtitleScrapeResult retried =
                        retryWithAlternativeVideo(curriculum, context, candidates, llmSelectedIds,
                                excludedVideoIds, selectedVideos, videoId);
                if (retried != null) {
                    chunks.addAll(retried.chunks());
                    transcriptCount = retried.chunks().size();
                }
            } else if (code == SubtitleErrorCode.RATE_LIMITED) {
                // 일시적 차단 — 학습실은 자막 없이 일단 완성하고, 60초 뒤 같은 영상으로 큐 재시도
                String curriculumId = curriculum.getId();
                subtitleRetryQueue.enqueue(
                        () -> self.retrySubtitleScrape(curriculumId, videoId),
                        SUBTITLE_RATE_LIMIT_RETRY_SEC
                );
            }
            // 그 외 코드(VIDEO_PRIVATE / AGE / REGION / NOT_FOUND / NETWORK / TIMEOUT 등)는
            // 자막 없이 Step C/D 로 진행한다. 학습실 자체는 완성되도록 둔다.
        } catch (Exception e) {
            log.warn("[{}주차] 자막 처리 중 예기치 못한 예외 (videoId={}): {}",
                    curriculum.getWeekNumber(), videoId, e.getMessage(), e);
        }

        // Step B-2: 보완 영상 자막 스크래핑 (best-effort, 실패해도 계속 진행)
        if (selectedVideos.size() > 1) {
            String secondaryVideoId = selectedVideos.get(1).videoId();
            try {
                SubtitleScrapeResult secondaryResult = scrapeWithCache(curriculum, secondaryVideoId);
                int secondaryCount = saveTranscripts(curriculum, secondaryVideoId, secondaryResult.chunks());
                chunks.addAll(secondaryResult.chunks());
                transcriptCount += secondaryCount;
                log.info("[{}주차] 보완 영상 자막 스크래핑 성공 — videoId={}, chunks={}",
                        curriculum.getWeekNumber(), secondaryVideoId, secondaryCount);
            } catch (Exception e) {
                log.info("[{}주차] 보완 영상 자막 스크래핑 실패 (무시) — videoId={}: {}",
                        curriculum.getWeekNumber(), secondaryVideoId, e.getMessage());
            }
        }

        // Step C: 자막 텍스트 기반 LLM 키워드 추출
        // 자막이 있을 때만 실행. 자막 없거나 실패 시 P1 key_concepts로 폴백.
        List<String> keywords = null;
        if (!chunks.isEmpty()) {
            keywords = extractKeywords(curriculum, chunks);
        }
        if (keywords == null || keywords.isEmpty()) {
            keywords = fallbackKeywords(curriculum, context);
            if (!keywords.isEmpty()) {
                log.info("[{}주차] 자막 키워드 없음 — P1 fallback 키워드 적용: {}개", curriculum.getWeekNumber(), keywords.size());
            }
        }
        if (keywords != null && !keywords.isEmpty()) {
            curriculum.updateKeywords(keywords);
            weeklyCurriculumRepository.save(curriculum);
        }

        // Step D: LLM 학습 자료 생성 → PDF 변환 → S3 업로드 → resources에 추가
        // Step B/C가 실패해도 topic/description만으로 자료 생성 가능
        generateAndUploadMaterials(curriculum, context);

        log.info("[{}주차] enrichment 완료 — videoId: {}, transcripts: {}개, keywords: {}",
                curriculum.getWeekNumber(), videoId, transcriptCount,
                keywords != null ? keywords.size() + "개" : "추출 실패");
    }

    // --- Step A: Algorithmic YouTube 영상 추천 ---

    /**
     * YouTube 검색 결과로 후보 풀을 구성한다. 점수 계산은 별도 메서드(pickBest)에서 수행한다.
     * 재시도(NO_SUBTITLES_AVAILABLE 차선책) 시에도 검색 결과를 재사용하기 위해 분리되어 있다.
     */
    private List<YoutubeApiService.VideoMeta> gatherCandidates(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        if (!youtubeApiService.isEnabled()) return List.of();

        String subject = nullSafe(context.subject()).trim();
        String topic = curriculumTopic(curriculum);
        String fullBase = (subject + " " + topic).replaceAll("\\s+", " ").trim();
        String searchQuery = nullSafe(context.youtubeSearchQuery());
        List<String> baseQueries = subjectTopicQueries(subject, topic, fullBase);

        List<YoutubeApiService.VideoMeta> allCandidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1차: videoDuration=long(>20분) — 체계적인 전체 강의 영상 우선 탐색 (YouTube API 하드 필터)
        for (String baseQuery : baseQueries) {
            if (baseQuery.isBlank() || allCandidates.size() >= 12) break;
            youtubeApiService.searchVideos(baseQuery + " 강의", 5, "long").stream()
                    .filter(v -> seen.add(v.videoId()))
                    .forEach(allCandidates::add);
        }

        // 2차: medium/기타 영상 보충 (videoDuration 미지정 → YouTube 기본값, closedCaption 우선 + any 폴백은 내부 처리)
        List<String> queries = new ArrayList<>();
        if (!searchQuery.isBlank()) queries.add(searchQuery);
        for (String baseQuery : baseQueries) {
            queries.add(baseQuery + " 개념 강의");
            queries.add(baseQuery);
        }
        for (String query : queries) {
            if (query.isBlank() || allCandidates.size() >= 12) break;
            youtubeApiService.searchVideos(query, 6).stream()
                    .filter(v -> seen.add(v.videoId()))
                    .forEach(allCandidates::add);
        }

        List<YoutubeApiService.VideoMeta> filtered = filterVideoCandidates(curriculum, context, allCandidates);
        if (filtered.size() < allCandidates.size()) {
            log.info("[{}주차] 유튜브 후보 필터링 — 원본 {}개 → 적합 {}개",
                    curriculum.getWeekNumber(), allCandidates.size(), filtered.size());
        }
        return filtered;
    }

    private List<String> subjectTopicQueries(String subject, String topic, String fullBase) {
        List<String> queries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (fullBase != null && !fullBase.isBlank() && seen.add(fullBase)) {
            queries.add(fullBase);
        }

        for (String alias : subjectSearchAliases(subject)) {
            String query = (alias + " " + nullSafe(topic)).replaceAll("\\s+", " ").trim();
            if (!query.isBlank() && seen.add(query)) {
                queries.add(query);
            }
        }
        return queries;
    }

    private List<String> subjectSearchAliases(String subject) {
        String normalizedSubject = normalizeForMatch(subject);
        if (containsAny(normalizedSubject, Set.of("프랑스어", "불어", "french", "francais", "français"))) {
            return List.of("프랑스어", "불어", "프랑스어 문법");
        }
        if (containsAny(normalizedSubject, Set.of("영어", "english"))) {
            return List.of("영어", "영문법");
        }
        if (containsAny(normalizedSubject, Set.of("일본어", "일어", "japanese"))) {
            return List.of("일본어", "일문법");
        }
        if (containsAny(normalizedSubject, Set.of("중국어", "chinese", "mandarin"))) {
            return List.of("중국어", "중문법");
        }
        if (containsAny(normalizedSubject, Set.of("러시아어", "russian"))) {
            return List.of("러시아어", "러시아어 문법");
        }
        if (containsAny(normalizedSubject, Set.of("독일어", "german", "deutsch"))) {
            return List.of("독일어", "독문법");
        }
        if (containsAny(normalizedSubject, Set.of("스페인어", "spanish", "espanol", "español"))) {
            return List.of("스페인어", "스페인어 문법");
        }
        return List.of();
    }

    private List<YoutubeApiService.VideoMeta> filterVideoCandidates(WeeklyCurriculum curriculum,
                                                                     WeekEnrichmentContext context,
                                                                     List<YoutubeApiService.VideoMeta> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(video -> isAcceptableVideoCandidate(curriculum, context, video))
                .toList();
    }

    private boolean isAcceptableVideoCandidate(WeeklyCurriculum curriculum,
                                               WeekEnrichmentContext context,
                                               YoutubeApiService.VideoMeta video) {
        return isAcceptableVideoCandidate(context.subject(), curriculumTopic(curriculum), context.keyConcepts(), video);
    }

    private boolean isAcceptableVideoCandidate(String subject,
                                               String targetTopic,
                                               List<String> keyConcepts,
                                               YoutubeApiService.VideoMeta video) {
        if (video == null || video.videoId() == null || video.videoId().isBlank()) return false;

        String title = nullSafe(video.title());
        String description = nullSafe(video.description());
        String combined = normalizeForMatch(title + " " + description);
        if (BAD_VIDEO_PATTERN.matcher(combined).find()) return false;
        if (META_VIDEO_PATTERN.matcher(combined).find()) return false;
        if (containsNonKoreanCjk(title)) return false;

        long duration = video.durationSec() != null ? video.durationSec() : 0;
        if (duration > 0 && duration < 300) return false;
        if (duration >= 10800) return false;

        LanguageSubjectProfile languageProfile = languageSubjectProfile(subject);

        // audioLang이 명시된 경우: 한국어 플랫폼이므로 외국어 과목이 아니면 비한국어 오디오 거부
        String audioLang = video.defaultAudioLanguage();
        if (audioLang != null && !audioLang.startsWith("ko")) {
            if (languageProfile == null) return false;
        }
        // audioLang 미확인 + 제목에 한국어 없음: 자동번역된 외국 영상 의심 → 거부
        if (audioLang == null && !hasKoreanChars(title)) return false;

        if (languageProfile != null) {
            if (containsAny(combined, languageProfile.conflictAliases())) return false;
            if (!containsAny(combined, languageProfile.evidenceTokens())) return false;
        }

        // hasTopicEvidence 하드 필터 제거 — 검색 쿼리가 이미 주제를 한정하고, LLM이 관련성 최종 검증
        return true;
    }

    private boolean hasTopicEvidence(String combinedText, String topic, List<String> keyConcepts) {
        List<String> tokens = new ArrayList<>();
        tokens.addAll(matchTokens(topic));
        if (keyConcepts != null) {
            keyConcepts.forEach(concept -> tokens.addAll(matchTokens(concept)));
        }
        if (tokens.isEmpty()) return true;
        return tokens.stream().anyMatch(combinedText::contains);
    }

    private List<String> matchTokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> stopwords = Set.of(
                "개념", "강의", "이론", "원리", "기초", "입문", "정리", "활용", "학습",
                "실전", "비교", "심화", "종합", "주차", "이번", "그리고", "및"
        );
        String normalized = normalizeForMatch(text).replaceAll("[^가-힣a-z0-9]+", " ");
        return Pattern.compile("\\s+").splitAsStream(normalized)
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !stopwords.contains(token))
                .distinct()
                .toList();
    }

    private int subjectTopicScore(WeeklyCurriculum curriculum,
                                  WeekEnrichmentContext context,
                                  YoutubeApiService.VideoMeta video) {
        return subjectTopicScore(context.subject(), curriculumTopic(curriculum), context.keyConcepts(), video);
    }

    private int subjectTopicScore(String subject,
                                  String targetTopic,
                                  List<String> keyConcepts,
                                  YoutubeApiService.VideoMeta video) {
        String title = nullSafe(video.title());
        String description = nullSafe(video.description());
        String combined = normalizeForMatch(title + " " + description);
        String normalizedSubject = normalizeForMatch(subject);

        int score = 0;
        if (!normalizedSubject.isBlank() && combined.contains(normalizedSubject)) score += 15;
        for (String token : matchTokens(targetTopic)) {
            if (combined.contains(token)) score += 8;
        }
        if (keyConcepts != null) {
            for (String concept : keyConcepts) {
                for (String token : matchTokens(concept)) {
                    if (combined.contains(token)) score += 4;
                }
            }
        }

        LanguageSubjectProfile profile = languageSubjectProfile(subject);
        if (profile != null && containsAny(combined, profile.evidenceTokens())) {
            score += 25;
        }
        return score;
    }

    private int scoreVideoCandidate(String subject,
                                    String targetTopic,
                                    List<String> keyConcepts,
                                    YoutubeApiService.VideoMeta video,
                                    Set<String> excludedVideoIds) {
        if (video == null || video.videoId() == null || video.videoId().isBlank()) return Integer.MIN_VALUE;
        if (excludedVideoIds != null && excludedVideoIds.contains(video.videoId())) return Integer.MIN_VALUE;
        if (!isAcceptableVideoCandidate(subject, targetTopic, keyConcepts, video)) return Integer.MIN_VALUE;

        int score = 0;
        String audioLang = video.defaultAudioLanguage();
        if (audioLang != null && audioLang.startsWith("ko")) {
            score += 25;
        } else if (audioLang != null) {
            // 비한국어 명시: 외국어 학습 과목의 대상 언어 오디오 (isAcceptableVideoCandidate를 통과한 경우)
            score += 5;
        } else {
            // audioLang 미확인: isAcceptableVideoCandidate에서 한국어 제목 검증을 통과했으므로 소폭 보너스
            score += 10;
        }
        if (video.hasCaptions()) score += 10;

        long duration = video.durationSec() != null ? video.durationSec() : 0;
        if (duration >= 1200 && duration <= 7200) {
            score += 15; // 20~120분: 체계적 강의
        } else if (duration >= 300) {
            score += 5;  // 5~20분: 개념 요약
        }

        String combined = nullSafe(video.title()) + " " + nullSafe(video.description());
        if (CONCEPT_VIDEO_PATTERN.matcher(combined).find()) score += 10;

        score += subjectTopicScore(subject, targetTopic, keyConcepts, video);
        return score;
    }

    private List<YoutubeApiService.VideoMeta> shortlistCandidatesForLlm(String subject,
                                                                         String targetTopic,
                                                                         List<String> keyConcepts,
                                                                         List<YoutubeApiService.VideoMeta> candidates,
                                                                         Set<String> excludedVideoIds,
                                                                         int limit) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Map<String, ScoredVideo> scoredById = new LinkedHashMap<>();
        for (YoutubeApiService.VideoMeta video : candidates) {
            int score = scoreVideoCandidate(subject, targetTopic, keyConcepts, video, excludedVideoIds);
            if (score == Integer.MIN_VALUE) continue;
            scoredById.putIfAbsent(video.videoId(), new ScoredVideo(video, score));
        }

        return scoredById.values().stream()
                .sorted(Comparator.comparingInt(ScoredVideo::score).reversed())
                .limit(Math.max(1, limit))
                .map(ScoredVideo::video)
                .toList();
    }

    private ScoredVideo bestScoredVideo(String subject,
                                        String targetTopic,
                                        List<String> keyConcepts,
                                        List<YoutubeApiService.VideoMeta> candidates,
                                        Set<String> excludedVideoIds) {
        if (candidates == null || candidates.isEmpty()) return null;
        // Integer.MIN_VALUE = isAcceptableVideoCandidate 실패. 초기 필터를 통과한 후보는 점수 하한 없이 최고 점수 수용.
        // YouTube API가 durationSec/audioLanguage를 미제공하면 점수가 0이 되어 기존 임계값(25)에서 탈락했던 문제 해결.
        return candidates.stream()
                .map(video -> new ScoredVideo(video, scoreVideoCandidate(subject, targetTopic, keyConcepts, video, excludedVideoIds)))
                .filter(scored -> scored.score() > Integer.MIN_VALUE)
                .max(Comparator.comparingInt(ScoredVideo::score))
                .orElse(null);
    }

    private record ScoredVideo(YoutubeApiService.VideoMeta video, int score) {}

    private String normalizeForMatch(String value) {
        return nullSafe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, Set<String> tokens) {
        if (text == null || text.isBlank() || tokens == null || tokens.isEmpty()) return false;
        return tokens.stream().map(this::normalizeForMatch).anyMatch(text::contains);
    }

    private record LanguageSubjectProfile(Set<String> evidenceTokens, Set<String> conflictAliases) {}

    private LanguageSubjectProfile languageSubjectProfile(String subject) {
        String normalizedSubject = normalizeForMatch(subject);
        if (normalizedSubject.isBlank()) return null;

        if (containsAny(normalizedSubject, Set.of("프랑스어", "불어", "french", "francais", "français"))) {
            return new LanguageSubjectProfile(
                    Set.of("프랑스어", "불어", "불문법", "프랑스 문법", "french", "francais", "français",
                            "qui", "que", "dont", "où", "lequel", "laquelle", "auquel", "duquel"),
                    languageConflictAliases("french")
            );
        }
        if (containsAny(normalizedSubject, Set.of("영어", "english"))) {
            return new LanguageSubjectProfile(
                    Set.of("영어", "영문법", "english", "grammar", "who", "which", "that", "whom", "whose"),
                    languageConflictAliases("english")
            );
        }
        if (containsAny(normalizedSubject, Set.of("일본어", "일어", "japanese"))) {
            return new LanguageSubjectProfile(
                    Set.of("일본어", "일문법", "japanese", "jlpt"),
                    languageConflictAliases("japanese")
            );
        }
        if (containsAny(normalizedSubject, Set.of("중국어", "chinese", "mandarin"))) {
            return new LanguageSubjectProfile(
                    Set.of("중국어", "중문법", "chinese", "mandarin", "hsk"),
                    languageConflictAliases("chinese")
            );
        }
        if (containsAny(normalizedSubject, Set.of("러시아어", "russian"))) {
            return new LanguageSubjectProfile(
                    Set.of("러시아어", "러문법", "russian"),
                    languageConflictAliases("russian")
            );
        }
        if (containsAny(normalizedSubject, Set.of("독일어", "german", "deutsch"))) {
            return new LanguageSubjectProfile(
                    Set.of("독일어", "독문법", "german", "deutsch"),
                    languageConflictAliases("german")
            );
        }
        if (containsAny(normalizedSubject, Set.of("스페인어", "spanish", "espanol", "español"))) {
            return new LanguageSubjectProfile(
                    Set.of("스페인어", "서문법", "spanish", "espanol", "español"),
                    languageConflictAliases("spanish")
            );
        }
        return null;
    }

    private Set<String> languageConflictAliases(String ownLanguage) {
        Map<String, Set<String>> aliases = Map.of(
                "french", Set.of("영어", "영문법", "english", "일본어", "일문법", "japanese", "중국어", "중문법", "chinese", "러시아어", "russian", "독일어", "german", "스페인어", "spanish"),
                "english", Set.of("프랑스어", "불어", "불문법", "french", "일본어", "일문법", "japanese", "중국어", "중문법", "chinese", "러시아어", "russian", "독일어", "german", "스페인어", "spanish"),
                "japanese", Set.of("프랑스어", "불어", "french", "영어", "영문법", "english", "중국어", "중문법", "chinese", "러시아어", "russian", "독일어", "german", "스페인어", "spanish"),
                "chinese", Set.of("프랑스어", "불어", "french", "영어", "영문법", "english", "일본어", "일문법", "japanese", "러시아어", "russian", "독일어", "german", "스페인어", "spanish"),
                "russian", Set.of("프랑스어", "불어", "french", "영어", "영문법", "english", "일본어", "일문법", "japanese", "중국어", "중문법", "chinese", "독일어", "german", "스페인어", "spanish"),
                "german", Set.of("프랑스어", "불어", "french", "영어", "영문법", "english", "일본어", "일문법", "japanese", "중국어", "중문법", "chinese", "러시아어", "russian", "스페인어", "spanish"),
                "spanish", Set.of("프랑스어", "불어", "french", "영어", "영문법", "english", "일본어", "일문법", "japanese", "중국어", "중문법", "chinese", "러시아어", "russian", "독일어", "german")
        );
        return aliases.getOrDefault(ownLanguage, Set.of());
    }

    /**
     * 로컬 점수 상위 5개 후보만 LLM에 넘겨 주차 주제용 영상 1개 또는 2개를 고른다.
     * 한 영상으로 모든 핵심 개념을 커버하기 어렵다면 두 번째 보완 영상을 함께 고르게 한다.
     * LLM 호출 실패 시 빈 리스트를 반환하고 기존 scoring 방식으로 폴백된다.
     */
    private List<String> llmSelectVideoIds(WeeklyCurriculum curriculum, WeekEnrichmentContext context,
                                           List<YoutubeApiService.VideoMeta> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        String subject = context.subject();
        String topic = curriculumTopic(curriculum);
        List<YoutubeApiService.VideoMeta> shortlist = shortlistCandidatesForLlm(
                subject, topic, context.keyConcepts(), candidates, null, LLM_VIDEO_CANDIDATE_LIMIT);
        if (shortlist.isEmpty()) return List.of();

        return requestLlmVideoSelection(
                (int) curriculum.getWeekNumber(),
                "주차 주제 영상",
                subject,
                topic,
                joinLines(context.learningObjectives()),
                context.keyConcepts(),
                shortlist,
                WEEK_VIDEO_MAX_SELECTION
        );
    }

    private List<String> requestLlmVideoSelection(int weekNumber,
                                                  String selectionType,
                                                  String subject,
                                                  String targetTopic,
                                                  String learningContext,
                                                  List<String> keyConcepts,
                                                  List<YoutubeApiService.VideoMeta> candidates,
                                                  int maxSelection) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        StringBuilder candidateText = new StringBuilder();
        for (YoutubeApiService.VideoMeta v : candidates) {
            String langInfo = v.defaultAudioLanguage() != null ? v.defaultAudioLanguage() : "미확인";
            String durationInfo = v.durationSec() != null ? v.durationSec() + "초" : "미확인";
            candidateText.append("- videoId: ").append(v.videoId())
                    .append(" | 언어: ").append(langInfo)
                    .append(" | 자막: ").append(v.hasCaptions() ? "있음" : "미확인/없음")
                    .append(" | 길이: ").append(durationInfo)
                    .append(" | 제목: ").append(nullSafe(v.title()))
                    .append(" | 설명: ").append(nullSafe(v.description()))
                    .append("\n");
        }

        String countRule = maxSelection == WEAKNESS_VIDEO_MAX_SELECTION
                ? "약점 키워드 보충 영상은 반드시 1개만 선택한다. 적합한 영상이 전혀 없을 때만 빈 배열을 반환한다."
                : "주차 주제 영상은 기본적으로 1개만 선택한다. 한 영상으로 학습실 주제와 주차 핵심 개념 전체를 커버하기 어렵다고 판단될 때만 2개까지 선택한다.";

        String systemPrompt = """
                당신은 MoAI 학습 플랫폼의 유튜브 영상 적합도 평가 AI입니다.

                시스템이 YouTube API 결과를 먼저 점수화해 최대 5개 후보만 추렸습니다.
                이 후보 중 현재 학습 맥락에 가장 잘 맞는 이론·개념 학습 영상을 최종 선택하세요.

                ■ 선택 기준
                1. 학습실 주제(과목)와 현재 목표 주제의 관련성이 높아야 한다.
                2. 개념·이론을 설명하는 강의형 영상이어야 한다.
                3. 문제풀이, 기출, 시험 전략, 공부법, 독학법, 로드맵, 채널 추천, 쇼츠, 브이로그는 제외한다.
                4. 과목이 언어 학습이면 대상 언어가 반드시 일치해야 한다. 그 외 과목은 반드시 한국어 강의여야 한다.
                5. 언어 정보가 'ko'가 아닌 값으로 명시된 영상은 언어 학습 과목이 아닌 이상 배제한다.
                6. 언어 정보가 '미확인'인 영상은 제목에 한국어가 포함되어 있어야 선택한다.
                7. 제목·설명이 현재 주제와 명백히 무관한 콘텐츠는 제외한다.
                8. 반드시 후보 목록에 있는 videoId만 반환한다.
                9. %s

                ■ 출력 형식: 순수 JSON (코드블록 금지)
                {"rankings": [{"videoId": "영상ID", "score": 0~10, "reason": "한 줄 이유"}]}
                - score 내림차순 정렬
                - 적합도 5점 이상인 영상만 포함
                - 적합한 영상이 없으면 빈 배열: {"rankings": []}
                """.formatted(countRule);

        String userMessage = String.format(
                "선택 목적: %s\n학습실 주제(과목): %s\n현재 목표 주제: %s\n학습 목표/맥락:\n%s\n핵심 개념/약점 키워드: %s\n\n영상 후보(로컬 점수 상위 %d개 이하):\n%s",
                selectionType,
                nullSafe(subject),
                nullSafe(targetTopic),
                nullSafe(learningContext),
                joinCsv(keyConcepts),
                LLM_VIDEO_CANDIDATE_LIMIT,
                candidateText
        );

        Set<String> candidateIds = candidates.stream()
                .map(YoutubeApiService.VideoMeta::videoId)
                .collect(Collectors.toSet());

        try {
            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();
            LlmVideoRankResponse response = llmService.callJson(request, LlmVideoRankResponse.class);
            if (response == null || response.getRankings() == null || response.getRankings().isEmpty()) {
                log.info("[{}주차] LLM 영상 선택({}) — 적합한 후보 없음, scoring 폴백",
                        weekNumber, selectionType);
                return List.of();
            }

            List<String> selectedIds = response.getRankings().stream()
                    .filter(item -> item.getScore() >= 5)
                    .map(LlmVideoRankResponse.RankingItem::getVideoId)
                    .filter(id -> id != null && !id.isBlank())
                    .filter(candidateIds::contains)
                    .distinct()
                    .limit(Math.max(1, maxSelection))
                    .toList();

            log.info("[{}주차] LLM 영상 선택({}) 완료 — 후보 {}개 중 {}개 선택: {}",
                    weekNumber, selectionType, candidates.size(), selectedIds.size(), String.join(", ", selectedIds));
            return selectedIds;
        } catch (Exception e) {
            log.warn("[{}주차] LLM 영상 선택({}) 실패 — scoring 폴백: {}",
                    weekNumber, selectionType, e.getMessage());
            return List.of();
        }
    }

    private CurriculumResource toYoutubeResource(WeeklyCurriculum curriculum, YoutubeApiService.VideoMeta video) {
        String title = (video.title() != null && !video.title().isBlank())
                ? video.title() : curriculum.getTopic();
        return new CurriculumResource(
                "youtube", video.videoId(), title, null, null,
                video.durationSec(), video.viewCount(), null
        );
    }

    /**
     * LLM 선택 결과가 있으면 그 순서를 따르고, 없으면 기존 scoring 방식으로 최대 2개를 고른다.
     */
    private List<YoutubeApiService.VideoMeta> pickVideosWithLlm(WeeklyCurriculum curriculum,
                                                                 WeekEnrichmentContext context,
                                                                 List<YoutubeApiService.VideoMeta> candidates,
                                                                 List<String> llmSelectedIds,
                                                                 Set<String> excludedVideoIds) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<YoutubeApiService.VideoMeta> selected = new ArrayList<>();
        if (llmSelectedIds != null && !llmSelectedIds.isEmpty()) {
            Map<String, YoutubeApiService.VideoMeta> candidateMap = new LinkedHashMap<>();
            for (YoutubeApiService.VideoMeta candidate : candidates) {
                if (candidate.videoId() == null || candidate.videoId().isBlank()) continue;
                candidateMap.putIfAbsent(candidate.videoId(), candidate);
            }

            for (String videoId : llmSelectedIds) {
                if (excludedVideoIds != null && excludedVideoIds.contains(videoId)) continue;
                YoutubeApiService.VideoMeta video = candidateMap.get(videoId);
                if (video == null) continue;
                boolean alreadySelected = selected.stream()
                        .anyMatch(v -> videoId.equals(v.videoId()));
                if (!alreadySelected) {
                    selected.add(video);
                }
                if (selected.size() == 2) break;
            }

            if (!selected.isEmpty()) {
                log.info("[{}주차] LLM 기반 영상 선택 — {}개: {}",
                        curriculum.getWeekNumber(),
                        selected.size(),
                        selected.stream()
                                .map(v -> nullSafe(v.videoId()))
                                .collect(Collectors.joining(", ")));
                return selected;
            }

            log.info("[{}주차] LLM 선택 후보가 검색 후보에 없음 — 기존 scoring 방식 폴백",
                    curriculum.getWeekNumber());
        }

        Set<String> localExcluded = new HashSet<>();
        if (excludedVideoIds != null) {
            localExcluded.addAll(excludedVideoIds);
        }
        for (int i = 0; i < 2; i++) {
            YoutubeApiService.VideoMeta next = pickBest(curriculum, context, candidates, localExcluded);
            if (next == null) break;
            selected.add(next);
            localExcluded.add(next.videoId());
        }

        if (!selected.isEmpty()) {
            log.info("[{}주차] scoring 기반 영상 선택 — {}개: {}",
                    curriculum.getWeekNumber(),
                    selected.size(),
                    selected.stream()
                            .map(v -> nullSafe(v.videoId()))
                            .collect(Collectors.joining(", ")));
        }
        return selected;
    }

    /**
     * LLM 랭킹이 있으면 순서대로 시도하고, 없거나 모두 제외된 경우 기존 scoring 방식으로 폴백한다.
     */
    private YoutubeApiService.VideoMeta pickBestWithLlm(WeeklyCurriculum curriculum,
                                                         WeekEnrichmentContext context,
                                                         List<YoutubeApiService.VideoMeta> candidates,
                                                         List<String> llmRankedIds,
                                                         Set<String> excludedVideoIds) {
        if (llmRankedIds != null && !llmRankedIds.isEmpty()) {
            Map<String, YoutubeApiService.VideoMeta> candidateMap = new LinkedHashMap<>();
            if (candidates != null) {
                for (YoutubeApiService.VideoMeta candidate : candidates) {
                    if (candidate.videoId() == null || candidate.videoId().isBlank()) continue;
                    candidateMap.putIfAbsent(candidate.videoId(), candidate);
                }
            }
            for (String videoId : llmRankedIds) {
                if (excludedVideoIds != null && excludedVideoIds.contains(videoId)) continue;
                YoutubeApiService.VideoMeta v = candidateMap.get(videoId);
                if (v != null) {
                    log.info("[{}주차] LLM 적합도 기반 영상 선택: {} (videoId={})",
                            curriculum.getWeekNumber(), v.title(), videoId);
                    return v;
                }
            }
            log.info("[{}주차] LLM 랭킹 후보가 모두 제외됨 — 기존 scoring 방식 폴백",
                    curriculum.getWeekNumber());
        }
        return pickBest(curriculum, context, candidates, excludedVideoIds);
    }

    /**
     * 미리 모은 후보 풀에서 점수 1등 영상을 고른다.
     * excludedVideoIds 에 포함된 영상은 후보에서 즉시 제외한다 (NO_SUBTITLES 재시도용).
     */
    private YoutubeApiService.VideoMeta pickBest(WeeklyCurriculum curriculum,
                                                  WeekEnrichmentContext context,
                                                  List<YoutubeApiService.VideoMeta> allCandidates,
                                                  Set<String> excludedVideoIds) {
        if (allCandidates == null || allCandidates.isEmpty()) return null;

        String subject = nullSafe(context.subject()).trim();
        ScoredVideo best = bestScoredVideo(subject, curriculumTopic(curriculum), context.keyConcepts(),
                allCandidates, excludedVideoIds);

        if (best != null) {
            log.info("[{}주차] 유튜브 매칭 성공: {} (score: {})",
                    curriculum.getWeekNumber(), best.video().title(), best.score());
            return best.video();
        }

        log.info("[{}주차] 엄격한 유튜브 매칭 실패 — 부정확한 영상 추천을 생략", curriculum.getWeekNumber());
        return null;
    }

    // --- Step B: 자막 청크 저장 + 재시도 로직 ---

    /**
     * 자막 스크래핑 전 글로벌 캐시 확인.
     * 같은 videoId 의 자막이 다른 학습실에서 이미 스크래핑된 적 있으면 그 chunks 를 그대로 재사용한다.
     * 캐시 미스면 실제 YouTube 호출.
     *
     * → 같은 영상은 시스템 전체에서 단 한 번만 YouTube 를 호출하게 되어 IP 밴 압력이 크게 줄어든다.
     * → 캐시 hit 시 lang 은 보존되지 않으므로 null, source 는 "cache" 로 표기한다.
     */
    private SubtitleScrapeResult scrapeWithCache(WeeklyCurriculum curriculum, String videoId) {
        Optional<VideoTranscript> sample =
                videoTranscriptRepository.findFirstByVideoIdOrderByChunkIndexAsc(videoId);
        if (sample.isPresent()) {
            String sourceCurriculumId = sample.get().getCurriculum().getId();
            List<VideoTranscript> cachedRows = videoTranscriptRepository
                    .findByCurriculumIdAndVideoIdOrderByChunkIndexAsc(sourceCurriculumId, videoId);
            if (!cachedRows.isEmpty()) {
                List<SubtitleChunk> chunks = cachedRows.stream()
                        .map(this::toSubtitleChunk)
                        .toList();
                log.info("[{}주차] 자막 캐시 hit — videoId={}, source curriculumId={}, chunks={}",
                        curriculum.getWeekNumber(), videoId, sourceCurriculumId, chunks.size());
                return new SubtitleScrapeResult(chunks, null, "cache");
            }
        }
        return subtitleScraper.scrape(videoId);
    }

    /**
     * VideoTranscript row 를 SubtitleChunk DTO 로 변환한다 (캐시 복제용).
     */
    private SubtitleChunk toSubtitleChunk(VideoTranscript row) {
        return new SubtitleChunk(
                row.getChunkIndex(),
                row.getTextContent(),
                row.getStartSec(),
                row.getEndSec()
        );
    }

    /**
     * 청크 리스트를 VideoTranscript 엔티티로 변환해 일괄 저장한다.
     *
     * @return 저장된 청크 개수
     */
    private int saveTranscripts(WeeklyCurriculum curriculum, String videoId, List<SubtitleChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0;
        List<VideoTranscript> transcripts = chunks.stream()
                .map(chunk -> VideoTranscript.builder()
                        .curriculum(curriculum)
                        .videoId(videoId)
                        .startSec(chunk.getStartSec())
                        .endSec(chunk.getEndSec())
                        .textContent(chunk.getText())
                        .chunkIndex(chunk.getChunkIndex())
                        .build())
                .toList();
        videoTranscriptRepository.saveAll(transcripts);
        return transcripts.size();
    }

    /**
     * NO_SUBTITLES_AVAILABLE 발생 시 후보 풀에서 차순위 영상을 골라 자막 스크래핑을 한 번만 더 시도한다.
     * 학습실 resources 의 youtube 항목을 새 영상으로 교체하고 transcripts 도 저장한다.
     *
     * @return 성공 시 결과, 후보가 없거나 또 실패 시 null
     */
    private SubtitleScrapeResult retryWithAlternativeVideo(WeeklyCurriculum curriculum,
                                                            WeekEnrichmentContext context,
                                                            List<YoutubeApiService.VideoMeta> candidates,
                                                            List<String> llmRankedIds,
                                                            Set<String> excludedVideoIds,
                                                            List<YoutubeApiService.VideoMeta> selectedVideos,
                                                            String failedVideoId) {
        YoutubeApiService.VideoMeta alternative = pickBestWithLlm(curriculum, context, candidates, llmRankedIds, excludedVideoIds);
        if (alternative == null) {
            log.info("[{}주차] 차선책 영상 없음 — 자막 없이 진행", curriculum.getWeekNumber());
            return null;
        }

        String altVideoId = alternative.videoId();
        String altTitle = (alternative.title() != null && !alternative.title().isBlank())
                ? alternative.title() : curriculum.getTopic();
        log.info("[{}주차] NO_SUBTITLES 차선책으로 재시도: videoId={}, title={}",
                curriculum.getWeekNumber(), altVideoId, altTitle);

        try {
            SubtitleScrapeResult retried = scrapeWithCache(curriculum, altVideoId);
            // 자막 추출에 성공한 차선책으로 실패한 주력 영상만 교체하고, 기존 보완 영상은 유지한다.
            List<CurriculumResource> normalVideos = new ArrayList<>();
            normalVideos.add(toYoutubeResource(curriculum, alternative));
            if (selectedVideos != null) {
                selectedVideos.stream()
                        .filter(v -> !altVideoId.equals(v.videoId()))
                        .filter(v -> failedVideoId == null || !failedVideoId.equals(v.videoId()))
                        .map(v -> toYoutubeResource(curriculum, v))
                        .forEach(normalVideos::add);
            }
            curriculum.updateResources(mergeNormalYoutubeResources(
                    curriculum.getResources(),
                    normalVideos.stream().limit(2).toList()));
            weeklyCurriculumRepository.save(curriculum);

            saveTranscripts(curriculum, altVideoId, retried.chunks());
            log.info("[{}주차] 차선책 영상 자막 스크래핑 성공: chunks={}",
                    curriculum.getWeekNumber(), retried.chunks().size());
            return retried;
        } catch (SubtitleScrapeException e) {
            log.warn("[{}주차] 차선책 영상도 실패 (videoId={}, code={}) — 자막 없이 진행",
                    curriculum.getWeekNumber(), altVideoId, e.getErrorCode());
            return null;
        }
    }

    /**
     * RATE_LIMITED 재시도 큐에서 60초 뒤에 호출되는 메서드.
     * 같은 영상으로 자막 스크래핑을 한 번만 더 시도한다 (무한 재시도 방지).
     * 별도 트랜잭션 경계가 필요하므로 @Async + @Transactional 로 표시한다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void retrySubtitleScrape(String curriculumId, String videoId) {
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(curriculumId).orElse(null);
        if (curriculum == null) {
            log.warn("자막 재시도 대상 커리큘럼을 찾을 수 없음: curriculumId={}", curriculumId);
            return;
        }

        try {
            SubtitleScrapeResult result = scrapeWithCache(curriculum, videoId);
            int count = saveTranscripts(curriculum, videoId, result.chunks());
            List<String> keywords = extractKeywords(curriculum, result.chunks());
            if (keywords != null && !keywords.isEmpty()) {
                curriculum.updateKeywords(keywords);
                weeklyCurriculumRepository.save(curriculum);
                log.info("[{}주차] 자막 재시도 후 키워드 갱신 완료 — {}개",
                        curriculum.getWeekNumber(), keywords.size());
            }
            log.info("[{}주차] 자막 재시도 성공 — videoId={}, chunks={}",
                    curriculum.getWeekNumber(), videoId, count);
        } catch (SubtitleScrapeException e) {
            log.warn("[{}주차] 자막 재시도 실패 (videoId={}, code={}) — 더 이상 재시도하지 않음",
                    curriculum.getWeekNumber(), videoId, e.getErrorCode());
        } catch (Exception e) {
            log.warn("[{}주차] 자막 재시도 중 예기치 못한 예외 (videoId={}): {}",
                    curriculum.getWeekNumber(), videoId, e.getMessage(), e);
        }
    }

    // --- Step C: LLM 키워드 추출 ---

    private List<String> extractKeywords(WeeklyCurriculum curriculum, List<SubtitleChunk> chunks) {
        try {
            // 전체 자막 텍스트를 하나로 합침
            String fullText = chunks.stream()
                    .map(SubtitleChunk::getText)
                    .collect(Collectors.joining(" "));

            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 강의 자막 분석 AI입니다.

                    역할: 한 주차의 전체 강의 자막에서 **시험 출제·복습·매칭에 활용 가능한 핵심 학습 키워드**를 정제해 추출합니다.
                    이 키워드 리스트는 이후 (a) 거꾸로 학습 평가의 허용 키워드 필터, (b) 파이널 퀴즈 생성의 소재, (c) 멘토-멘티 매칭의 기준으로 사용됩니다. 따라서 학술적 정확성과 범위 일관성이 최우선입니다.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 금지)
                    {"keywords": ["키워드1", "키워드2", ...]}

                    ■ 필수 규칙
                    1. 개수는 5~10개. 주차 주제 전체를 균형 있게 대표하도록 선정.
                    2. 각 키워드는 명사/명사구 단위. 영문 원어가 일반적으로 통용되면 "한글(영문)" 형태 권장 (예: "정규화(Normalization)"). 괄호가 어색하면 한글 단독 허용.
                    3. 학습 단위가 되는 **개념/원리/알고리즘/모델 이름**을 우선. 예시·실습 코드의 라이브러리명·변수명·인명·브랜드는 제외.
                    4. 지나치게 포괄적인 단어(예: "프로그래밍", "데이터")는 제외. 구체적·차별화된 용어로.
                    5. 중복·동의어·표기 변형은 1개로 통합.
                    6. 키워드가 전혀 파악되지 않으면 빈 배열 반환: {"keywords": []}

                    ■ 금지 사항
                    - 강의 속 진행 멘트·인사·광고 문구를 키워드화하지 말 것.
                    - 문장/구절을 그대로 옮겨 넣지 말 것 (명사구 단위로 정제).
                    """;

            String userMessage = String.format(
                    "학습 주제: %s\n\n강의 자막 전문:\n%s",
                    curriculum.getTopic(), fullText
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmKeywordResponse response = llmService.callJson(request, LlmKeywordResponse.class);
            return response.getKeywords();
        } catch (Exception e) {
            log.warn("[{}주차] LLM 키워드 추출 실패 (재시도 포함): {}", curriculum.getWeekNumber(), e.getMessage());
            pushLlmErrorSse(curriculum, curriculum.getWeekNumber() + "주차 키워드 추출에 실패했습니다.");
            return null;
        }
    }

    // --- Step D: LLM 학습 자료 생성 + PDF 변환 + S3 업로드 ---

    /**
     * LLM으로 학습 자료 콘텐츠를 생성한 뒤, PDF로 변환하여 S3에 업로드한다.
     * LLM 호출 실패 시 파일 생성을 스킵하지만, PDF URL이 존재하면 항상 DB에 저장한다.
     */
    private void generateAndUploadMaterials(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        // 1. LLM으로 구조화된 학습 자료 콘텐츠 생성
        MaterialContent content = generateMaterialContent(curriculum, context);
        if (content == null) {
            log.warn("[{}주차] 학습 자료 콘텐츠 생성 실패 — PDF 생성 스킵", curriculum.getWeekNumber());
            return;
        }

        // 기존 resources(YouTube 영상 등)를 유지하면서 PDF를 추가
        List<CurriculumResource> resources = new ArrayList<>(
                curriculum.getResources() != null ? curriculum.getResources() : List.of()
        );

        String materialTitle = curriculum.getWeekNumber() + "주차 학습 자료";
        String subject = nullSafe(context.subject()).replaceAll("[\\s/]", "_");
        String levelKo = switch (nullSafe(context.level())) {
            case "beginner" -> "기초";
            case "intermediate" -> "중급";
            case "advanced" -> "고급";
            default -> nullSafe(context.level());
        };
        int totalWeeks = curriculum.getRoom().getDurationWeeks();
        String s3Directory = "materials/" + subject + "_" + levelKo + "_" + totalWeeks + "주";
        String fileBaseName = curriculum.getWeekNumber() + "주차_" + curriculum.getTopic().replaceAll("[\\s/\\[\\]]", "_");

        // 2. Markdown 생성 → S3 업로드
        try {
            byte[] mdBytes = materialGeneratorService.generateMarkdown(content);
            String mdUrl = s3Service.upload(
                    s3Directory, fileBaseName + ".md", mdBytes, "text/markdown"
            );
            String mdSize = formatFileSize(mdBytes.length);
            if (mdUrl != null) {
                resources.add(new CurriculumResource("md", null, materialTitle, mdUrl, mdSize, null, null, null));
                log.info("[{}주차] Markdown 업로드 완료 — size: {}", curriculum.getWeekNumber(), mdSize);
            } else {
                log.info("[{}주차] Markdown 생성 완료 ({}) — S3 비활성화로 저장 스킵", curriculum.getWeekNumber(), mdSize);
            }
        } catch (Exception e) {
            log.warn("[{}주차] Markdown 생성/업로드 실패: {}", curriculum.getWeekNumber(), e.getMessage());
        }

        // 3. resources JSON 업데이트 — PDF 추가 여부와 관계없이 항상 DB에 저장
        curriculum.updateResources(resources);
        weeklyCurriculumRepository.save(curriculum);
    }

    /**
     * LLM을 호출하여 학습 자료의 구조화된 콘텐츠를 생성한다.
     * keywords가 있으면 프롬프트에 포함하여 더 정확한 자료를 생성한다.
     */
    private MaterialContent generateMaterialContent(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        try {
            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 학습 콘텐츠 생성 전문가 AI입니다.

                    아래 주차 정보를 바탕으로 가독성 높은 상세 학습 자료를 생성하세요.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                    {
                      "week_number": N,
                      "study_material": "마크다운 학습 자료 전문"
                    }

                    ■ study_material 구성 (순서 준수):

                    [1] ### 📌 이번 주차 개요
                    이번 주차 내용의 배경과 흐름, 학습 목표를 2~3문단으로 서술.

                    [2] ### 📖 핵심 개념
                    주제에 필요한 만큼의 개념을 아래 형식으로 작성:
                      #### 개념명
                      - 하나의 #### 섹션은 반드시 단 하나의 개념만 다룰 것. 절대 두 개 이상의 개념을 한 섹션 제목에 묶지 말 것.
                      - 금지 예시: "#### 제1정규형(1NF) 및 제2정규형(2NF)" — 이렇게 쓰면 절대 안 됨.
                      - 허용 예시: "#### 제1정규형(1NF)", "#### 제2정규형(2NF)" — 각각 별도 섹션으로.
                      - 섹션 구성 규칙 (반드시 준수):
                        1. 섹션 시작부에 해당 개념이 무엇인지 설명하는 2~3문장의 산문(불릿 없이 일반 문장)을 먼저 작성할 것.
                        2. 이후 세부 내용(특징, 원리, 하위 항목, 관련 개념 등)은 불릿(-)과 들여쓰기 불릿(  -)으로 작성.
                        3. 불릿은 세부 항목이 여러 개일 때 사용. 설명 전체를 불릿으로 도배하지 말 것.
                      - ①②③ 같은 원형 숫자 특수문자 사용 금지. 번호 목록이 필요하면 1. 2. 3. 형태만 사용.
                      - 소제목(원리, 예시, 주의사항 등의 세부 헤더) 분리는 금지
                      - 각 개념당 최소 500자 이상 서술할 것
                      개념 간 구분은 --- (수평선)으로 구분.

                    [3] ### 📊 비교 정리
                    비교가 유의미한 개념들을 마크다운 표로 정리.
                    표 수는 주제에 따라 자유롭게.

                    [4] ### 🚨 자주 하는 실수
                    혼동하기 쉬운 오개념·함정 포인트를 항목별로 서술.

                    ■ 마크다운 디자인 규칙 (핵심 — 반드시 준수):
                    - 긴 설명은 절대 문단 하나로 뭉치지 말 것. 불릿(-)과 들여쓰기 불릿(  -)을 적극 활용해 시각적으로 분리할 것.
                    - 섹션마다 디자인을 조금씩 다르게: 어떤 개념은 불릿 중심, 어떤 개념은 불릿+인용구(>) 조합, 어떤 개념은 표+불릿 조합 등 마크다운 요소를 다양하게 혼용.
                    - > 인용구는 해당 섹션에서 가장 핵심적인 정의나 원칙 한 줄에 가끔 사용. 남발하지 말 것.
                    - 사용 가능한 마크다운 요소: *이탤릭*, `인라인코드`, > 인용구, --- 구분선, | 표, - 불릿, 들여쓰기 불릿, 1. 번호목록.
                    - **볼드** 사용 규칙 (렌더링 안전을 위해 엄격히 준수):
                      - 볼드는 불릿 항목의 맨 앞 키워드에만 사용. 예: `- **완전 함수 종속**: 설명...`
                      - 문장 중간에 볼드 삽입 금지 (파서 호환성 문제 발생).
                      - 한 불릿 라인에 볼드(**...**) 를 두 번 이상 사용 금지.
                    - 헤더(###, ####)와 본문 사이 빈 줄 필수.
                    - 한 문단 최대 3~4줄. 그 이상이면 불릿 분리 또는 단락 분리.
                    - **헤더 레벨 엄격 제한**: study_material 내에서 ## (h2) 절대 사용 금지. 반드시 ### (h3) 또는 #### (h4) 만 사용.
                    - **표 셀 단일 라인 규칙**: 마크다운 표(|) 내 각 셀에는 줄바꿈(\\n) 금지. • 기호나 - 불릿을 셀 안에 넣지 말 것. 셀 하나에 내용 한 줄만 작성.

                    ■ 콘텐츠 규칙:
                    1. study_material 최소 3500자 이상. 각 개념마다 500자 이상 서술할 것.
                    2. 개념 수와 표 수는 주제에 따라 가변적으로 결정.
                    3. 실전 시나리오, 시험 포인트, 체크리스트 섹션은 포함하지 말 것.
                    4. 모든 내용은 한국어. 전문 용어는 영어 병기 (예: 원자성(Atomicity)).
                    """;

            String keywordsLine = "";
            List<String> finalKeywords = curriculum.getKeywords() != null && !curriculum.getKeywords().isEmpty()
                    ? curriculum.getKeywords()
                    : context.keyConcepts();
            if (finalKeywords != null && !finalKeywords.isEmpty()) {
                keywordsLine = "\n핵심 키워드: " + String.join(", ", finalKeywords);
            }

            String userMessage = String.format(
                    "과목: %s\n난이도: %s\n주차: %d주차\n학습 주제: %s\n주차 요약: %s\n학습 목표:\n%s\n집중 질문:\n%s\n복습 키워드: %s%s",
                    nullSafe(context.subject()),
                    nullSafe(context.level()),
                    (int) curriculum.getWeekNumber(),
                    curriculum.getTopic(),
                    nullSafe(context.weeklySummary()),
                    joinLines(context.learningObjectives()),
                    joinLines(context.focusQuestions()),
                    joinCsv(context.practiceKeywords()),
                    keywordsLine
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            LlmWeekDetailResponse detail = llmService.callJson(request, LlmWeekDetailResponse.class);
            return mapToMaterialContent(curriculum, detail);
        } catch (Exception e) {
            log.warn("[{}주차] LLM 학습 자료 생성 실패 (재시도 포함): {}", curriculum.getWeekNumber(), e.getMessage());
            pushLlmErrorSse(curriculum, curriculum.getWeekNumber() + "주차 학습 자료 생성에 실패했습니다.");
            return null;
        }
    }

    private MaterialContent mapToMaterialContent(WeeklyCurriculum curriculum, LlmWeekDetailResponse detail) {
        if (detail.getStudyMaterial() == null || detail.getStudyMaterial().isBlank()) {
            return null;
        }

        String title = curriculum.getWeekNumber() + "주차 학습 자료 — " + curriculum.getTopic();
        List<MaterialContent.Section> sections = List.of(
                new MaterialContent.Section("", detail.getStudyMaterial())
        );
        return new MaterialContent(title, sections);
    }

    /**
     * 바이트 크기를 사람이 읽기 쉬운 형태로 변환한다. (예: "245KB", "1.2MB")
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private String curriculumTopic(WeeklyCurriculum curriculum) {
        if (curriculum == null || curriculum.getTopic() == null) return "";
        return curriculum.getTopic()
                .replaceAll("(?i)^week\\s*\\d+\\s*[:\\-]\\s*", "")
                .trim();
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String joinLines(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        return items.stream().map(i -> "- " + i).collect(Collectors.joining("\n"));
    }

    private String joinCsv(List<String> items) {
        return items == null || items.isEmpty() ? "" : String.join(", ", items);
    }

    private void applyFallbackKeywordsIfMissing(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        if (curriculum.getKeywords() != null && !curriculum.getKeywords().isEmpty()) return;

        List<String> keywords = fallbackKeywords(curriculum, context);
        if (keywords.isEmpty()) return;

        curriculum.updateKeywords(keywords);
        weeklyCurriculumRepository.save(curriculum);
    }

    private List<String> fallbackKeywords(WeeklyCurriculum curriculum, WeekEnrichmentContext context) {
        List<String> keyConcepts = cleanKeywordList(context.keyConcepts());
        if (!keyConcepts.isEmpty()) return keyConcepts;

        List<String> practiceKeywords = cleanKeywordList(context.practiceKeywords());
        if (!practiceKeywords.isEmpty()) return practiceKeywords;

        String topic = curriculum.getTopic();
        if (topic != null && !topic.isBlank()) return List.of(topic.trim());
        return Collections.emptyList();
    }

    private List<String> cleanKeywordList(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<CurriculumResource> mergeNormalYoutubeResources(List<CurriculumResource> current,
                                                                  List<CurriculumResource> normalVideos) {
        List<CurriculumResource> merged = new ArrayList<>();
        if (normalVideos != null) {
            normalVideos.forEach(resource -> addResourceIfAbsent(merged, resource));
        }

        if (current != null) {
            current.stream()
                    .filter(resource -> !"youtube".equals(resource.getType()) || "weakness".equals(resource.getTag()))
                    .forEach(resource -> addResourceIfAbsent(merged, resource));
        }
        return merged;
    }

    private boolean hasWeaknessResource(WeeklyCurriculum curriculum, String type) {
        return curriculum.getResources() != null &&
                curriculum.getResources().stream()
                        .anyMatch(resource -> type.equals(resource.getType())
                                && "weakness".equals(resource.getTag()));
    }

    private void addResourceIfAbsent(List<CurriculumResource> resources, CurriculumResource candidate) {
        if (candidate == null) return;
        boolean exists = resources.stream().anyMatch(existing -> sameResource(existing, candidate));
        if (!exists) {
            resources.add(candidate);
        }
    }

    private boolean sameResource(CurriculumResource a, CurriculumResource b) {
        if (a == null || b == null) return false;
        if (!stringEquals(a.getType(), b.getType())) return false;
        if (a.getVideoId() != null || b.getVideoId() != null) {
            return stringEquals(a.getVideoId(), b.getVideoId());
        }
        if (a.getUrl() != null || b.getUrl() != null) {
            return stringEquals(a.getUrl(), b.getUrl());
        }
        return stringEquals(a.getTitle(), b.getTitle()) && stringEquals(a.getTag(), b.getTag());
    }

    private boolean stringEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // --- 약점 키워드 보충 enrichment ---

    /**
     * 파이널 퀴즈 완료 후 미해소 약점 키워드가 있을 때 다음 주차에 보충 콘텐츠를 추가한다.
     * 영상(최소 수로 커버), 통합 학습 자료 1개, 약점 퀴즈 1세트를 생성한다.
     */
    @Async("curriculumTaskExecutor")
    @Transactional
    public void enrichWithWeaknessKeywords(String curriculumId, List<String> weaknessKeywords,
                                            String subject, String level, short originalWeekNumber) {
        if (weaknessKeywords == null || weaknessKeywords.isEmpty()) return;

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findById(curriculumId).orElse(null);
        if (curriculum == null) {
            log.warn("weakness enrichment 대상 커리큘럼을 찾을 수 없음: curriculumId={}", curriculumId);
            return;
        }

        log.info("[{}주차→{}주차 약점 보충] enrichment 시작 — keywords: {}",
                originalWeekNumber, curriculum.getWeekNumber(), weaknessKeywords);

        // Step W-A: 약점 키워드별 YouTube 영상 검색 (videoId 기준 중복 제거)
        boolean hasWeaknessVideo = hasWeaknessResource(curriculum, "youtube");
        List<CurriculumResource> weaknessVideos = hasWeaknessVideo
                ? List.of()
                : findVideosForWeaknessKeywords(curriculum, weaknessKeywords, subject);
        if (!hasWeaknessVideo && !weaknessVideos.isEmpty()) {
            List<CurriculumResource> resources = new ArrayList<>(
                    curriculum.getResources() != null ? curriculum.getResources() : List.of());
            weaknessVideos.forEach(resource -> addResourceIfAbsent(resources, resource));
            curriculum.updateResources(resources);
            weeklyCurriculumRepository.save(curriculum);
        } else if (hasWeaknessVideo) {
            log.info("[{}주차 약점 보충] 영상 이미 존재 — 영상 검색 스킵", originalWeekNumber);
        }

        // Step W-B: 약점 키워드 통합 학습 자료 1개 생성
        if (!hasWeaknessResource(curriculum, "md")) {
            generateAndUploadWeaknessMaterial(curriculum, weaknessKeywords, subject, level, originalWeekNumber);
        } else {
            log.info("[{}주차 약점 보충] 자료 이미 존재 — 자료 생성 스킵", originalWeekNumber);
        }

        // Step W-C: 약점 퀴즈 생성
        generateWeaknessQuiz(curriculum, weaknessKeywords);

        log.info("[{}주차 약점 보충] enrichment 완료 — {}개 키워드, {}개 영상",
                originalWeekNumber, weaknessKeywords.size(), weaknessVideos.size());
    }

    private List<CurriculumResource> findVideosForWeaknessKeywords(WeeklyCurriculum curriculum,
                                                                    List<String> keywords,
                                                                    String subject) {
        if (!youtubeApiService.isEnabled()) return List.of();

        List<String> cleanKeywords = cleanKeywordList(keywords);
        if (cleanKeywords.isEmpty()) return List.of();
        String targetTopic = String.join(", ", cleanKeywords);

        // videoId 기준으로 중복 제거한 뒤, 로컬 점수 상위 5개만 LLM 최종 선택에 넘긴다.
        Map<String, YoutubeApiService.VideoMeta> byVideoId = new LinkedHashMap<>();
        for (String keyword : cleanKeywords) {
            List<String> queries = new ArrayList<>();
            if (!nullSafe(subject).isBlank()) {
                queries.add(subject + " " + keyword + " 개념 강의");
                queries.add(subject + " " + keyword + " 이론 강의");
            }
            queries.add(keyword + " 개념 강의");
            queries.add(keyword + " 강의");

            for (String query : queries) {
                if (byVideoId.size() >= 12) break;
                youtubeApiService.searchVideos(query, 5).stream()
                        .filter(v -> v.videoId() != null && !v.videoId().isBlank())
                        .filter(v -> isAcceptableVideoCandidate(subject, targetTopic, cleanKeywords, v))
                        .forEach(v -> byVideoId.putIfAbsent(v.videoId(), v));
            }
        }

        List<YoutubeApiService.VideoMeta> candidates = new ArrayList<>(byVideoId.values());
        List<YoutubeApiService.VideoMeta> shortlist = shortlistCandidatesForLlm(
                subject, targetTopic, cleanKeywords, candidates, null, LLM_VIDEO_CANDIDATE_LIMIT);
        if (shortlist.isEmpty()) {
            log.info("[{}주차] 약점 키워드 영상 후보 없음 — keywords={}",
                    curriculum.getWeekNumber(), cleanKeywords);
            return List.of();
        }

        List<String> selectedIds = requestLlmVideoSelection(
                (int) curriculum.getWeekNumber(),
                "약점 키워드 보충 영상",
                subject,
                targetTopic,
                "약점 키워드 개념 보충: " + targetTopic,
                cleanKeywords,
                shortlist,
                WEAKNESS_VIDEO_MAX_SELECTION
        );

        Map<String, YoutubeApiService.VideoMeta> candidateMap = candidates.stream()
                .collect(Collectors.toMap(
                        YoutubeApiService.VideoMeta::videoId,
                        video -> video,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        YoutubeApiService.VideoMeta selected = selectedIds.stream()
                .map(candidateMap::get)
                .filter(video -> video != null)
                .findFirst()
                .orElseGet(() -> {
                    ScoredVideo fallback = bestScoredVideo(subject, targetTopic, cleanKeywords, candidates, null);
                    return fallback != null ? fallback.video() : null;
                });

        if (selected == null) {
            log.info("[{}주차] 약점 키워드 영상 선택 실패 — 부정확한 영상 추천을 생략", curriculum.getWeekNumber());
            return List.of();
        }

        log.info("[{}주차] 약점 키워드 영상 선택 — videoId={}, title={}",
                curriculum.getWeekNumber(), selected.videoId(), selected.title());
        return List.of(new CurriculumResource(
                "youtube", selected.videoId(), selected.title(), null, null,
                selected.durationSec(), selected.viewCount(), "weakness"));
    }

    private boolean containsNonKoreanCjk(String text) {
        if (text == null) return false;
        // 일본어 히라가나/가타카나는 즉시 거부
        if (text.chars().anyMatch(c -> (c >= 0x3040 && c <= 0x30FF) || (c >= 0x31F0 && c <= 0x31FF))) return true;
        // CJK 통합 한자가 많고 한글(Hangul)이 없으면 중국어로 판단
        long cjkCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long koreanCount = text.chars().filter(c -> (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF)).count();
        return cjkCount > 3 && koreanCount == 0;
    }

    private boolean hasKoreanChars(String text) {
        if (text == null || text.isBlank()) return false;
        return text.chars().anyMatch(c -> (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF));
    }

    private void generateAndUploadWeaknessMaterial(WeeklyCurriculum curriculum,
                                                    List<String> weaknessKeywords,
                                                    String subject, String level,
                                                    short originalWeekNumber) {
        try {
            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 학습 콘텐츠 생성 전문가 AI입니다.

                    학습자가 파이널 퀴즈에서 부족했던 약점 키워드들을 집중 보완하는 상세 학습 자료를 생성하세요.

                    ■ 출력 형식: 순수 JSON (코드블록/마크다운 절대 금지)
                    {"study_material": "마크다운 학습 자료 전문"}

                    ■ study_material 구성 (순서 준수):

                    [1] ### 🔁 약점 보충 학습 목표
                    왜 이 키워드들이 부족했는지, 무엇을 집중 보완해야 하는지 2~3문단으로 서술.

                    [2] ### 🔍 키워드별 심화 학습
                    각 약점 키워드마다 아래 형식으로 작성:
                      #### 키워드명
                      - 하나의 #### 섹션은 반드시 단 하나의 키워드만 다룰 것. 절대 두 개 이상을 한 섹션에 묶지 말 것.
                      - 섹션 구성 규칙 (반드시 준수):
                        1. 섹션 시작부에 해당 키워드가 무엇인지 설명하는 2~3문장의 산문(불릿 없이 일반 문장)을 먼저 작성.
                        2. 이후 세부 내용(원리, 특징, 예시, 관련 개념 등)은 불릿(-)과 들여쓰기 불릿(  -)으로 작성.
                        3. 불릿은 세부 항목이 여러 개일 때 사용. 설명 전체를 불릿으로 도배하지 말 것.
                      - ①②③ 같은 원형 숫자 특수문자 사용 금지. 번호 목록은 1. 2. 3. 형태만 사용.
                      - 소제목(원리, 예시, 주의사항 등의 세부 헤더) 분리 금지.
                      - 각 키워드당 최소 400자 이상 서술할 것.
                      키워드 간 구분은 --- (수평선)으로 구분.

                    [3] ### 📊 비교 정리
                    키워드들 간 차이점·공통점을 마크다운 표로 정리.
                    표 수는 키워드에 따라 자유롭게.

                    [4] ### 🚨 자주 하는 실수
                    이 키워드들과 관련해 혼동하기 쉬운 오개념·함정 포인트를 항목별로 서술.

                    ■ 마크다운 디자인 규칙 (핵심 — 반드시 준수):
                    - 긴 설명은 절대 문단 하나로 뭉치지 말 것. 불릿(-)과 들여쓰기 불릿(  -)을 적극 활용해 시각적으로 분리할 것.
                    - 섹션마다 디자인을 조금씩 다르게: 어떤 키워드는 불릿 중심, 어떤 키워드는 불릿+인용구(>) 조합, 어떤 키워드는 표+불릿 조합 등 마크다운 요소를 다양하게 혼용.
                    - > 인용구는 해당 섹션에서 가장 핵심적인 정의나 원칙 한 줄에 가끔 사용. 남발하지 말 것.
                    - 사용 가능한 마크다운 요소: *이탤릭*, `인라인코드`, > 인용구, --- 구분선, | 표, - 불릿, 들여쓰기 불릿, 1. 번호목록.
                    - **볼드** 사용 규칙 (렌더링 안전을 위해 엄격히 준수):
                      - 볼드는 불릿 항목의 맨 앞 키워드에만 사용. 예: `- **완전 함수 종속**: 설명...`
                      - 문장 중간에 볼드 삽입 금지 (파서 호환성 문제 발생).
                      - 한 불릿 라인에 볼드(**...**) 를 두 번 이상 사용 금지.
                    - 헤더(###, ####)와 본문 사이 빈 줄 필수.
                    - 한 문단 최대 3~4줄. 그 이상이면 불릿 분리 또는 단락 분리.

                    ■ 콘텐츠 규칙:
                    1. study_material 최소 2500자 이상. 각 키워드마다 400자 이상 서술할 것.
                    2. 표 수와 키워드 수는 약점 키워드에 따라 가변적으로 결정.
                    3. 실전 시나리오, 시험 포인트, 체크리스트 섹션은 포함하지 말 것.
                    4. 모든 내용은 한국어. 전문 용어는 영어 병기 (예: 정규화(Normalization)).
                    """;

            String userMessage = String.format(
                    "과목: %s\n난이도: %s\n%d주차 약점 키워드: %s",
                    nullSafe(subject), nullSafe(level),
                    (int) originalWeekNumber,
                    String.join(", ", weaknessKeywords)
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            WeaknessMaterialResponse response = llmService.callJson(request, WeaknessMaterialResponse.class);
            if (response == null || response.getStudyMaterial() == null || response.getStudyMaterial().isBlank()) {
                log.warn("[{}주차 약점 보충] LLM 응답 없음", originalWeekNumber);
                return;
            }

            MaterialContent content = new MaterialContent(
                    originalWeekNumber + "주차 약점 보충 자료",
                    List.of(new MaterialContent.Section("약점 보충 학습", response.getStudyMaterial()))
            );

            String subjectKey = nullSafe(subject).replaceAll("[\\s/]", "_");
            String levelKo = switch (nullSafe(level)) {
                case "beginner" -> "기초";
                case "intermediate" -> "중급";
                case "advanced" -> "고급";
                default -> nullSafe(level);
            };
            int totalWeeks = curriculum.getRoom().getDurationWeeks();
            String s3Directory = "materials/" + subjectKey + "_" + levelKo + "_" + totalWeeks + "주";
            String keywordsSlug = weaknessKeywords.stream()
                    .limit(3)
                    .map(k -> k.replaceAll("[\\s/\\[\\]()]", "_"))
                    .collect(Collectors.joining("_"));
            String fileBaseName = originalWeekNumber + "주차_약점보충_" + keywordsSlug;
            String materialTitle = originalWeekNumber + "주차 약점 보충 자료";

            List<CurriculumResource> resources = new ArrayList<>(
                    curriculum.getResources() != null ? curriculum.getResources() : List.of());

            try {
                byte[] mdBytes = materialGeneratorService.generateMarkdown(content);
                String mdUrl = s3Service.upload(s3Directory, fileBaseName + ".md", mdBytes, "text/markdown");
                String mdSize = formatFileSize(mdBytes.length);
                if (mdUrl != null) {
                    resources.add(new CurriculumResource("md", null, materialTitle, mdUrl, mdSize, null, null, "weakness"));
                    log.info("[{}주차 약점 보충] Markdown 업로드 완료 — url: {}, size: {}", originalWeekNumber, mdUrl, mdSize);
                } else {
                    log.info("[{}주차 약점 보충] Markdown 생성 완료 ({}) — URL 없음, 리소스 미등록", originalWeekNumber, mdSize);
                }
            } catch (Exception e) {
                log.warn("[{}주차 약점 보충] Markdown 생성/업로드 실패: {}", originalWeekNumber, e.getMessage());
            }

            curriculum.updateResources(resources);
            weeklyCurriculumRepository.save(curriculum);

        } catch (Exception e) {
            log.warn("[{}주차 약점 보충] 학습 자료 생성 실패: {}", originalWeekNumber, e.getMessage());
            pushLlmErrorSse(curriculum, originalWeekNumber + "주차 약점 보충 자료 생성에 실패했습니다.");
        }
    }

    private void generateWeaknessQuiz(WeeklyCurriculum curriculum, List<String> weaknessKeywords) {
        try {
            if (quizRepository.findByCurriculumIdAndQuizType(curriculum.getId(), "weakness_remediation").isPresent()) {
                return;
            }

            List<String> capped = weaknessKeywords.stream().limit(5).toList();

            String systemPrompt = """
                    당신은 MoAI 학습 플랫폼의 약점 보충 퀴즈 출제 AI입니다.

                    학습자가 파이널 퀴즈에서 부족했던 약점 키워드를 타겟으로 객관식 퀴즈를 생성하세요.

                    ■ 출력: 순수 JSON (코드블록 없이)
                    {
                      "questions": [
                        {
                          "question": "객관식 문제 (개념 이해를 직접 검증하는 질문)",
                          "options": [
                            {"label": "A", "text": "보기1"},
                            {"label": "B", "text": "보기2"},
                            {"label": "C", "text": "보기3"},
                            {"label": "D", "text": "보기4"}
                          ],
                          "answer": "정답라벨",
                          "related_keyword": "관련 약점 키워드",
                          "explanation": "정답 근거 (2~3문장, 오답 분석 포함)"
                        }
                      ]
                    }

                    ■ 필수 규칙:
                    1. 각 약점 키워드당 최소 1문제, 최대 5문제 총합
                    2. 4지선다 객관식, 단순 암기가 아닌 이해도 검증 질문
                    3. related_keyword는 반드시 입력된 약점 키워드 중 하나
                    4. explanation에 왜 다른 선택지가 틀렸는지도 간략히 포함
                    """;

            String userMessage = String.format(
                    "주차 주제: %s\n약점 키워드: %s",
                    curriculum.getTopic(), String.join(", ", capped)
            );

            LlmRequestDto request = LlmRequestDto.builder()
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .build();

            WeaknessQuizResponse response = llmService.callJson(request, WeaknessQuizResponse.class);
            if (response == null || response.getQuestions() == null || response.getQuestions().isEmpty()) {
                log.warn("[{}주차] 약점 퀴즈 LLM 응답 없음", curriculum.getWeekNumber());
                return;
            }

            Quiz quiz = Quiz.builder()
                    .curriculum(curriculum)
                    .quizType("weakness_remediation")
                    .title(curriculum.getWeekNumber() + "주차 약점 보충 퀴즈")
                    .build();
            quizRepository.save(quiz);

            short order = 1;
            for (WeaknessQuizResponse.QuestionItem qi : response.getQuestions()) {
                List<QuizOption> options = qi.getOptions() == null ? List.of() :
                        qi.getOptions().stream()
                                .map(o -> new QuizOption(o.getLabel(), o.getText()))
                                .toList();

                QuizQuestion question = QuizQuestion.builder()
                        .quiz(quiz)
                        .questionType("multiple")
                        .question(qi.getQuestion())
                        .options(options)
                        .answer(qi.getAnswer())
                        .questionOrder(order++)
                        .relatedKeyword(qi.getRelatedKeyword())
                        .build();
                quizQuestionRepository.save(question);
            }

            log.info("[{}주차] 약점 퀴즈 생성 완료 — {}문제", curriculum.getWeekNumber(), response.getQuestions().size());
        } catch (Exception e) {
            log.warn("[{}주차] 약점 퀴즈 생성 실패 (재시도 포함): {}", curriculum.getWeekNumber(), e.getMessage());
            pushLlmErrorSse(curriculum, curriculum.getWeekNumber() + "주차 약점 보충 퀴즈 생성에 실패했습니다.");
        }
    }

    private void pushLlmErrorSse(WeeklyCurriculum curriculum, String message) {
        try {
            String userId = curriculum.getRoom().getUser().getId();
            notificationService.pushSse(userId, new SseSimpleEvent("llm_error", message));
        } catch (Exception ex) {
            log.warn("LLM 오류 SSE 전송 실패: {}", ex.getMessage());
        }
    }

    // --- LLM 응답 DTO (내부 클래스) ---

    @Getter
    @NoArgsConstructor
    static class WeaknessMaterialResponse {
        @JsonProperty("study_material")
        private String studyMaterial;
    }

    @Getter
    @NoArgsConstructor
    static class WeaknessQuizResponse {
        private List<QuestionItem> questions;

        @Getter
        @NoArgsConstructor
        static class QuestionItem {
            private String question;
            private List<OptionItem> options;
            private String answer;

            @JsonProperty("related_keyword")
            private String relatedKeyword;

            private String explanation;
        }

        @Getter
        @NoArgsConstructor
        static class OptionItem {
            private String label;
            private String text;
        }
    }

    @Getter
    @NoArgsConstructor
    static class LlmVideoRankResponse {
        private List<RankingItem> rankings;

        @Getter
        @NoArgsConstructor
        static class RankingItem {
            @JsonProperty("videoId")
            private String videoId;
            private int score;
            private String reason;
        }
    }

    @Getter
    @NoArgsConstructor
    static class LlmVideoResponse {
        @JsonProperty("video_id")
        private String videoId;
        private String title;
    }

    @Getter
    @NoArgsConstructor
    static class LlmKeywordResponse {
        private List<String> keywords;
    }

    @Getter
    @NoArgsConstructor
    static class LlmWeekDetailResponse {
        @JsonProperty("week_number")
        private Integer weekNumber;

        @JsonProperty("study_material")
        private String studyMaterial;
    }
}
