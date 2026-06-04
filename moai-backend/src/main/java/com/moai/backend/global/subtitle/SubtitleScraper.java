package com.moai.backend.global.subtitle;

import com.moai.backend.global.subtitle.dto.SubtitleScrapeResult;

/**
 * 자막 스크래핑 추상화.
 *
 * 구현체:
 * - {@link YtdlpSubtitleScraper}     — yt-dlp + youtube-transcript-api (Python 서브프로세스). 로컬/폴백용.
 * - {@link SupadataSubtitleScraper}  — Supadata 관리형 API. EC2(클라우드 IP) 환경에서 봇 탐지 우회.
 *
 * 호출부는 본 인터페이스에만 의존한다. subtitle.provider 프로퍼티(ytdlp/supadata)로
 * 주입되는 구현체가 결정된다.
 */
public interface SubtitleScraper {

    SubtitleScrapeResult scrape(String videoId);
}
