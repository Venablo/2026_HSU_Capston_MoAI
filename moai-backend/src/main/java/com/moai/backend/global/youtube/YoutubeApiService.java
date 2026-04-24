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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
                .queryParam("part", "snippet,status,contentDetails")
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
        return Optional.of(new VideoMeta(id, title, "https://www.youtube.com/watch?v=" + id, true));
    }

    public List<VideoMeta> searchVideos(String query, int maxResults) {
        if (!isEnabled() || query == null || query.isBlank()) return Collections.emptyList();

        int size = Math.max(1, Math.min(maxResults, 10));
        JsonNode root = fetchWithFallback(key -> b -> b.path("/search")
                .queryParam("part", "snippet")
                .queryParam("q", query)
                .queryParam("type", "video")
                .queryParam("videoEmbeddable", "true")
                .queryParam("videoSyndicated", "true")
                .queryParam("maxResults", size)
                .queryParam("relevanceLanguage", "ko")
                .queryParam("regionCode", "KR")
                .queryParam("safeSearch", "moderate")
                .queryParam("key", key)
                .build());
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
                .queryParam("part", "snippet,status,contentDetails")
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
            results.add(new VideoMeta(id, title, "https://www.youtube.com/watch?v=" + id, true));
        }
        return results;
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

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    public record VideoMeta(String videoId, String title, String url, boolean embeddable) {}
}
