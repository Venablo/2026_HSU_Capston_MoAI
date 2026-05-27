package com.moai.backend.domain.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 목업 템플릿 시드 요청.
 * videoId 와 topic 만으로 mock_curriculum_templates + mock_transcript_templates 양쪽에
 * 적절한 값을 자동으로 채워 INSERT 한다.
 */
@Getter
@NoArgsConstructor
public class MockTemplateSeedRequestDto {

    @NotBlank(message = "videoId 는 필수입니다")
    @Size(max = 20, message = "videoId 는 최대 20자")
    private String videoId;

    @NotBlank(message = "topic 은 필수입니다")
    @Size(max = 200, message = "topic 은 최대 200자")
    private String topic;
}
