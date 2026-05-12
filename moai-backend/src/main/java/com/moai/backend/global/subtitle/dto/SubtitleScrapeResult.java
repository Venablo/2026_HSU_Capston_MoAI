package com.moai.backend.global.subtitle.dto;

import java.util.List;

/**
 * 자막 스크래핑 성공 결과.
 *
 * @param chunks 자막 청크 리스트 (순서대로, chunk_index 0부터 시작)
 * @param lang   실제로 추출된 자막 언어 (예: "ko", "en")
 * @param source 추출 경로 — "yt-dlp" 또는 "youtube-transcript-api"
 */
public record SubtitleScrapeResult(
        List<SubtitleChunk> chunks,
        String lang,
        String source
) {
}
