package com.moai.backend.global.subtitle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.global.subtitle.dto.SubtitleChunk;
import com.moai.backend.global.subtitle.dto.SubtitleScrapeResult;
import com.moai.backend.global.subtitle.exception.SubtitleErrorCode;
import com.moai.backend.global.subtitle.exception.SubtitleScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Supadata Transcript API 기반 자막 스크래퍼.
 *
 * subtitle.provider=supadata 일 때 활성화된다. EC2 등 클라우드 IP 환경에서
 * YouTube 봇 탐지(IP 밴) 우회 목적.
 *
 * 호출:
 * - 엔드포인트: GET /v1/transcript
 * - 인증: x-api-key 헤더 (환경변수 SUPADATA_API_KEY 로 주입)
 * - 쿼리: url, text=false (시간 청크 보존), mode=native (기존 자막만 — 1크레딧)
 *
 * HTTP status → SubtitleErrorCode 매핑:
 *   200      → 즉시 변환
 *   202      → jobId 폴링 (최대 30초)
 *   206      → NO_SUBTITLES_AVAILABLE
 *   401/403  → SUPADATA_AUTH_FAILED
 *   402      → SUPADATA_QUOTA_EXCEEDED
 *   404      → VIDEO_NOT_FOUND
 *   429      → RATE_LIMITED
 *   5xx / 네트워크 → NETWORK_ERROR
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "subtitle.provider", havingValue = "supadata")
public class SupadataSubtitleScraper implements SubtitleScraper {

    private static final String TRANSCRIPT_PATH = "/v1/transcript";

    /**
     * 폴링 한도. 15회 × 2초 = 약 30초.
     * 기존 yt-dlp 의 subtitle.timeout-sec=30 정책과 일치시켜 @Async 스레드 점유 시간을 제한한다.
     * 초과 시 SCRIPT_TIMEOUT 으로 자막 포기 (학습실 자체는 자막 없이 완성됨).
     */
    private static final int MAX_POLL_ATTEMPTS = 15;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private static final String SOURCE_LABEL = "supadata";

    private final WebClient supadataWebClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public SupadataSubtitleScraper(@Qualifier("supadataWebClient") WebClient supadataWebClient,
                                   ObjectMapper objectMapper,
                                   @Value("${supadata.api-key}") String apiKey) {
        this.supadataWebClient = supadataWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public SubtitleScrapeResult scrape(String videoId) {
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        try {
            return supadataWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path(TRANSCRIPT_PATH)
                            .queryParam("url", videoUrl)
                            .queryParam("text", "false")   // 시간 청크 배열 필수
                            .queryParam("mode", "native")  // 기존 자막만 — 1크레딧
                            .build())
                    .header("x-api-key", apiKey)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> handleResponse(videoId, response.statusCode(), body)))
                    .block();
        } catch (SubtitleScrapeException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw mapStatusToException(videoId, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Supadata 호출 네트워크/타임아웃 실패: videoId={}, msg={}", videoId, e.getMessage());
            throw new SubtitleScrapeException(SubtitleErrorCode.NETWORK_ERROR,
                    "Supadata 호출 실패: " + e.getMessage(), e);
        }
    }

    /** 1차 응답(status + body) 을 결과로 변환하거나 폴링/예외로 분기. */
    private SubtitleScrapeResult handleResponse(String videoId, HttpStatusCode status, String body) {
        int code = status.value();
        if (code == 200) {
            return parseTranscriptBody(videoId, body);
        }
        if (code == 202) {
            String jobId = extractJobId(body);
            if (jobId == null || jobId.isBlank()) {
                throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                        "Supadata 202 응답에 jobId 없음");
            }
            return pollJob(videoId, jobId);
        }
        throw mapStatusToException(videoId, status, body);
    }

    /** 비동기 처리(202) — jobId 로 status 폴링. 한도 초과 시 SCRIPT_TIMEOUT. */
    private SubtitleScrapeResult pollJob(String videoId, String jobId) {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_EXECUTION_FAILED,
                        "Supadata 폴링 중 인터럽트", ie);
            }
            HttpStatusCode pollStatus;
            String pollBody;
            try {
                Object[] response = supadataWebClient.get()
                        .uri(uriBuilder -> uriBuilder.path(TRANSCRIPT_PATH + "/" + jobId).build())
                        .header("x-api-key", apiKey)
                        .exchangeToMono(r -> r.bodyToMono(String.class).defaultIfEmpty("")
                                .map(b -> new Object[]{r.statusCode(), b}))
                        .block();
                pollStatus = (HttpStatusCode) response[0];
                pollBody = (String) response[1];
            } catch (Exception e) {
                log.warn("Supadata 폴링 네트워크 오류: videoId={}, jobId={}, msg={}", videoId, jobId, e.getMessage());
                throw new SubtitleScrapeException(SubtitleErrorCode.NETWORK_ERROR,
                        "Supadata 폴링 실패: " + e.getMessage(), e);
            }
            if (!pollStatus.is2xxSuccessful()) {
                throw mapStatusToException(videoId, pollStatus, pollBody);
            }
            JsonNode root = readTree(pollBody);
            String jobStatus = root.path("status").asText("");
            if ("completed".equalsIgnoreCase(jobStatus)) {
                return parseTranscriptBody(videoId, pollBody);
            }
            if ("failed".equalsIgnoreCase(jobStatus) || "error".equalsIgnoreCase(jobStatus)) {
                throw new SubtitleScrapeException(SubtitleErrorCode.UNKNOWN_ERROR,
                        "Supadata 작업 실패: " + root.path("error").asText("(no detail)"));
            }
            // 그 외 (queued / processing 등) → 다음 시도
        }
        log.warn("Supadata 폴링 한도 초과: videoId={}, jobId={}", videoId, jobId);
        throw new SubtitleScrapeException(SubtitleErrorCode.SCRIPT_TIMEOUT,
                "Supadata 폴링 한도 초과: jobId=" + jobId);
    }

    /** 200/완료된 폴링 응답 본문(JSON) → SubtitleScrapeResult. offset/duration(ms) → 초로 변환. */
    private SubtitleScrapeResult parseTranscriptBody(String videoId, String body) {
        JsonNode root = readTree(body);
        JsonNode contentNode = root.get("content");
        if (contentNode == null || !contentNode.isArray()) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                    "Supadata 응답에 content 배열 없음");
        }
        List<SubtitleChunk> chunks = new ArrayList<>(contentNode.size());
        int index = 0;
        for (JsonNode entry : contentNode) {
            String text = entry.path("text").asText("");
            double offsetMs = entry.path("offset").asDouble(0.0);
            double durationMs = entry.path("duration").asDouble(0.0);
            double startSec = offsetMs / 1000.0;
            double durationSec = durationMs / 1000.0;
            chunks.add(SubtitleChunk.of(index++, text, startSec, durationSec));
        }
        String lang = root.path("lang").asText(null);
        log.info("Supadata 자막 추출 성공: videoId={}, lang={}, chunks={}", videoId, lang, chunks.size());
        return new SubtitleScrapeResult(chunks, lang, SOURCE_LABEL);
    }

    private String extractJobId(String body) {
        return readTree(body).path("jobId").asText(null);
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception e) {
            throw new SubtitleScrapeException(SubtitleErrorCode.INVALID_SCRIPT_OUTPUT,
                    "Supadata 응답 JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private SubtitleScrapeException mapStatusToException(String videoId, HttpStatusCode status, String body) {
        int code = status.value();
        String detail = "videoId=" + videoId + ", status=" + code + ", body=" + truncate(body);
        SubtitleErrorCode mapped = switch (code) {
            case 206 -> SubtitleErrorCode.NO_SUBTITLES_AVAILABLE;
            case 401, 403 -> SubtitleErrorCode.SUPADATA_AUTH_FAILED;
            case 402 -> SubtitleErrorCode.SUPADATA_QUOTA_EXCEEDED;
            case 404 -> SubtitleErrorCode.VIDEO_NOT_FOUND;
            case 429 -> SubtitleErrorCode.RATE_LIMITED;
            default -> (code >= 500 && code <= 599)
                    ? SubtitleErrorCode.NETWORK_ERROR
                    : SubtitleErrorCode.UNKNOWN_ERROR;
        };
        return new SubtitleScrapeException(mapped, detail);
    }

    private String truncate(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
