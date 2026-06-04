package com.moai.backend.domain.material.dto;

import com.moai.backend.domain.material.entity.CustomMaterial;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class MaterialListResponseDto {

    private List<MaterialSummary> materials;

    @Getter
    @AllArgsConstructor
    public static class MaterialSummary {
        private String materialId;
        private String title;
        private List<String> triggerKeywords;
        private String videoSegment;
        private LocalDateTime createdAt;

        public static MaterialSummary from(CustomMaterial material) {
            return new MaterialSummary(
                    material.getId(),
                    material.getTitle(),
                    material.getTriggerKeywords(),
                    material.getVideoSegment(),
                    material.getCreatedAt()
            );
        }
    }
}
