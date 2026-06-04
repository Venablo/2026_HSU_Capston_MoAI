package com.moai.backend.global.material;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM이 생성한 학습 자료의 구조화된 콘텐츠.
 * PDF/DOCX 생성 시 공통 입력으로 사용된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialContent {

    private String title;
    private List<Section> sections;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String heading;
        private String content;
    }
}
