package com.moai.backend.domain.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 목업 템플릿 시드 결과.
 * 시드 후 클라이언트가 확인할 수 있도록 생성된 templateId/weekNumber 와
 * LLM 추출/자막 청크 수량을 함께 반환한다.
 */
@Getter
@AllArgsConstructor
public class MockTemplateSeedResponseDto {
    private final Long templateId;
    private final Short weekNumber;
    private final int keywordCount;
    private final int chunkCount;
}
