package com.moai.backend.global.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeApiService {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";
    private static final Pattern QUOTA_PATTERN =
            Pattern.compile("quotaExceeded|youtube\\.quota|exceeded your .*quota", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE_DISABLED_PATTERN =
            Pattern.compile("accessNotConfigured|SERVICE_DISABLED|YouTube Data API", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    @Value("${youtube.api-key:}")
    private String primaryKey;

    @Value("${youtube.fallback-keys:}")
    private String fallbackKeysRaw;

    private List<String> apiKeys = Collections.emptyList();
    private WebClient webClient;

    @PostConstruct
    void init() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (primaryKey != null && !primaryKey.isBlank()) {
            keys.add(primaryKey.trim());
        }
        if (fallbackKeysRaw != null && !fallbackKeysRaw.isBlank()) {
            Arrays.stream(fallbackKeysRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(keys::add);
        }
        this.apiKeys = new ArrayList<>(keys);
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        if (apiKeys.isEmpty()) {
            log.info("YouTube API 키가 설정되지 않음 — YouTube 검증/검색 비활성화");
        } else {
            log.info("YouTube API 키 {}개 로드 완료 (1차 + fallback)", apiKeys.size());
        }
    }

    public boolean isEnabled() {
        return !apiKeys.isEmpty();
    }

    public Optional<VideoMeta> verifyVideo(String videoId) {
        if (!isEnabled() || videoId == null || videoId.isBlank()) return Optional.empty();

        JsonNode root = fetchWithFallback(key -> b -> b.path("/videos")
                .queryParam("part", "snippet,status,contentDetails,statistics")
                .queryParam("id", videoId)
                .queryParam("key", key)
                .build());
        if (root == null) return Optional.empty();

        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) return Optional.empty();

        JsonNode item = items.get(0);
        String id = item.path("id").asText(null);
        if (id == null || id.isBlank()) return Optional.empty();

        String privacy = item.path("status").path("privacyStatus").asText("public");
        boolean embeddable = item.path("status").path("embeddable").asBoolean(true);
        if (!"public".equalsIgnoreCase(privacy) || !embeddable) return Optional.empty();

        String title = item.path("snippet").path("title").asText("");
        Long durationSec = parseDuration(item.path("contentDetails").path("duration").asText(null));
        Long viewCount   = parseViewCount(item.path("statistics").path("viewCount").asText(null));
        boolean hasCaptions = "true".equalsIgnoreCase(item.path("contentDetails").path("caption").asText("false"));
        String description = truncate(item.path("snippet").path("description").asText(""), 300);
        String audioLang = audioLanguage(item.path("snippet"));
        return Optional.of(new VideoMeta(id, title, "https://www.youtube.com/watch?v=" + id, true, durationSec, viewCount, hasCaptions, description, audioLang));
    }

    /** duration 없이 검색. closedCaption 우선, 결과 부족 시 any로 폴백. */
    public List<VideoMeta> searchVideos(String query, int maxResults) {
        return searchVideos(query, maxResults, null);
    }

    /**
     * YouTube API 파라미터로 걸 수 있는 것은 모두 API 레벨에서 필터링한다.
     * - videoCaption=closedCaption 우선 → 결과 2개 미만이면 any로 폴백 (한국 채널 중 자막 미업로드 채널 포함)
     * - videoDuration: "long"(>20분) / "medium"(4~20분) 지정 가능. null이면 YouTube 기본(전체)
     * - regionCode=KR, relevanceLanguage=ko, videoEmbeddable, videoSyndicated, safeSearch → 모두 API 하드 필터
     *
     * API로 걸 수 없는 것(defaultAudioLanguage, 제목 내 CJK/정크 키워드 등)은 호출 측에서 처리한다.
     */
    public List<VideoMeta> searchVideos(String query, int maxResults, String videoDuration) {
        if (!isEnabled() || query == null || query.isBlank()) return Collections.emptyList();

        int size = Math.max(1, Math.min(maxResults, 10));

        // 1차: closedCaption — 자막 업로드 채널 = 대체로 정제된 교육 콘텐츠
        List<VideoMeta> primary = doSearchAndVerify(query, size, videoDuration, "closedCaption");
        if (primary.size() >= 2) return primary;

        // 결과 부족 시 videoCaption 조건 제거해 재검색
        List<VideoMeta> fallback = doSearchAndVerify(query, size, videoDuration, "any");
        if (primary.isEmpty()) return fallback;

        Set<String> seen = new HashSet<>();
        primary.forEach(v -> seen.add(v.videoId()));
        List<VideoMeta> combined = new ArrayList<>(primary);
        fallback.stream().filter(v -> seen.add(v.videoId())).forEach(combined::add);
        return combined;
    }

    private List<VideoMeta> doSearchAndVerify(String query, int size, String videoDuration, String videoCaption) {
        JsonNode root = fetchWithFallback(key -> b -> {
            UriBuilder ub = b.path("/search")
                    .queryParam("part", "snippet")
                    .queryParam("q", query)
                    .queryParam("type", "video")
                    .queryParam("videoEmbeddable", "true")
                    .queryParam("videoSyndicated", "true")
                    .queryParam("videoCaption", videoCaption)
                    .queryParam("maxResults", size)
                    .queryParam("relevanceLanguage", "ko")
                    .queryParam("regionCode", "KR")
                    .queryParam("safeSearch", "moderate");
            if (videoDuration != null && !videoDuration.isBlank()) {
                ub = ub.queryParam("videoDuration", videoDuration);
            }
            return ub.queryParam("key", key).build();
        });
        if (root == null) return Collections.emptyList();

        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) return Collections.emptyList();

        List<String> ids = new ArrayList<>();
        for (JsonNode item : items) {
            String videoId = item.path("id").path("videoId").asText(null);
            if (videoId != null && !videoId.isBlank()) ids.add(videoId);
        }
        if (ids.isEmpty()) return Collections.emptyList();

        String joinedIds = String.join(",", ids);
        JsonNode verified = fetchWithFallback(key -> b -> b.path("/videos")
                .queryParam("part", "snippet,status,contentDetails,statistics")
                .queryParam("id", joinedIds)
                .queryParam("key", key)
                .build());
        if (verified == null) return Collections.emptyList();

        List<VideoMeta> results = new ArrayList<>();
        for (JsonNode v : verified.path("items")) {
            String id = v.path("id").asText(null);
            if (id == null || id.isBlank()) continue;
            String privacy = v.path("status").path("privacyStatus").asText("public");
            boolean embeddable = v.path("status").path("embeddable").asBoolean(true);
            if (!"public".equalsIgnoreCase(privacy) || !embeddable) continue;
            String title = v.path("snippet").path("title").asText("");
            Long durationSec = parseDuration(v.path("contentDetails").path("duration").asText(null));
            Long viewCount   = parseViewCount(v.path("statistics").path("viewCount").asText(null));
            boolean hasCaptions = "true".equalsIgnoreCase(v.path("contentDetails").path("caption").asText("false"));
            String description = truncate(v.path("snippet").path("description").asText(""), 300);
            String audioLang = audioLanguage(v.path("snippet"));
            results.add(new VideoMeta(id, title, "https://www.youtube.com/watch?v=" + id, true, durationSec, viewCount, hasCaptions, description, audioLang));
        }
        return results;
    }

    private static Long parseDuration(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Duration.parse(iso).getSeconds();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Long parseViewCount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode fetchWithFallback(Function<String, Function<UriBuilder, java.net.URI>> uriBuilderPerKey) {
        for (int i = 0; i < apiKeys.size(); i++) {
            String key = apiKeys.get(i);
            try {
                String response = webClient.get()
                        .uri(uriBuilderPerKey.apply(key))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                if (response == null) continue;
                return objectMapper.readTree(response);
            } catch (WebClientResponseException e) {
                String body = e.getResponseBodyAsString();
                boolean quota = e.getStatusCode().value() == 403 && QUOTA_PATTERN.matcher(body).find();
                boolean disabled = e.getStatusCode().value() == 403 && SERVICE_DISABLED_PATTERN.matcher(body).find();
                if (quota) {
                    log.warn("YouTube API 키 #{} 할당량 초과 — 다음 키로 폴백", i + 1);
                    continue;
                }
                if (disabled) {
                    log.warn("YouTube Data API 비활성화 상태 — 검증/검색 중단 (HTTP {})", e.getStatusCode().value());
                    return null;
                }
                log.warn("YouTube API 호출 실패 (키 #{}, HTTP {}): {}",
                        i + 1, e.getStatusCode().value(), truncate(body));
                return null;
            } catch (Exception e) {
                log.warn("YouTube API 네트워크 오류 (키 #{}): {}", i + 1, e.getMessage());
                if (i < apiKeys.size() - 1) continue;
                return null;
            }
        }
        log.warn("YouTube API 모든 키 소진 — 후속 로직은 LLM 추천값을 그대로 사용");
        return null;
    }

    /** snippet 노드에서 실제 오디오/콘텐츠 언어를 추출한다. defaultAudioLanguage → defaultLanguage 순으로 시도. */
    private static String audioLanguage(JsonNode snippet) {
        String audioLang = snippet.path("defaultAudioLanguage").asText(null);
        if (audioLang != null && !audioLang.isBlank()) return audioLang.toLowerCase();
        String defLang = snippet.path("defaultLanguage").asText(null);
        return (defLang != null && !defLang.isBlank()) ? defLang.toLowerCase() : null;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public record VideoMeta(String videoId, String title, String url, boolean embeddable,
                            Long durationSec, Long viewCount, boolean hasCaptions, String description,
                            String defaultAudioLanguage) {}
}
