package com.moai.backend.domain.curriculum.dto;

import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CurriculumDetailResponseDto {

    private String weekId;
    private int weekNumber;
    private String topic;
    private String description;
    private List<String> keywords;
    private String mainVideoId;
    private List<CurriculumResource> resources;
    private BigDecimal completionRate;

    public static CurriculumDetailResponseDto from(WeeklyCurriculum curriculum) {
        // resources에서 youtube 타입의 첫 번째 videoId를 mainVideoId로 추출
        String mainVideoId = null;
        if (curriculum.getResources() != null) {
            mainVideoId = curriculum.getResources().stream()
                    .filter(r -> "youtube".equals(r.getType()))
                    .map(CurriculumResource::getVideoId)
                    .findFirst()
                    .orElse(null);
        }

        // docs 탭에는 pdf/docx/zip 등 다운로드 가능한 자료만 내려준다.
        // youtube 타입은 url=null 이므로 제외 (별도 recommended-videos API 사용).
        List<CurriculumResource> docResources = curriculum.getResources() == null
                ? Collections.emptyList()
                : curriculum.getResources().stream()
                        .filter(r -> !"youtube".equals(r.getType()))
                        .toList();

        return CurriculumDetailResponseDto.builder()
                .weekId(curriculum.getId())
                .weekNumber(curriculum.getWeekNumber())
                .topic(curriculum.getTopic())
                .description(curriculum.getDescription())
                .keywords(resolveKeywords(curriculum))
                .mainVideoId(mainVideoId)
                .resources(docResources)
                .completionRate(curriculum.getCompletionRate())
                .build();
    }

    private static List<String> resolveKeywords(WeeklyCurriculum curriculum) {
        List<String> keywords = cleanKeywords(curriculum.getKeywords());
        if (!keywords.isEmpty()) return keywords;

        String topic = curriculum.getTopic();
        if (topic != null && !topic.isBlank()) return List.of(topic.trim());
        return Collections.emptyList();
    }

    private static List<String> cleanKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                cleaned.add(keyword.trim());
            }
        }
        return new ArrayList<>(cleaned);
    }
}
