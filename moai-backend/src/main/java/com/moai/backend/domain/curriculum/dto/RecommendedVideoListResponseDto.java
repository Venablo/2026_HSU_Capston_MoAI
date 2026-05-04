package com.moai.backend.domain.curriculum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class RecommendedVideoListResponseDto {

    private List<VideoSummary> videos;

    @Getter
    @AllArgsConstructor
    public static class VideoSummary {
        private String videoId;
        private String title;
        private Long durationSec;
        private Long viewCount;
        private String tag; // "weakness" for remediation videos, null for normal
    }
}
