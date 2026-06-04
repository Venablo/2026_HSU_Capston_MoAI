package com.moai.backend.domain.material.dto;

import com.moai.backend.domain.material.entity.CustomMaterial;
import com.moai.backend.domain.material.entity.SummaryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MaterialDetailResponseDto {

    private String materialId;
    private String title;
    private List<String> triggerKeywords;
    private String videoSegment;
    private List<SummaryItem> summaryItems;
    private LocalDateTime createdAt;

    public static MaterialDetailResponseDto from(CustomMaterial material) {
        return MaterialDetailResponseDto.builder()
                .materialId(material.getId())
                .title(material.getTitle())
                .triggerKeywords(material.getTriggerKeywords())
                .videoSegment(material.getVideoSegment())
                .summaryItems(material.getSummaryItems())
                .createdAt(material.getCreatedAt())
                .build();
    }
}
