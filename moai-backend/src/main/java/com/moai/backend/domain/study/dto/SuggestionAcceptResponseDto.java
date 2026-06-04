package com.moai.backend.domain.study.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SuggestionAcceptResponseDto {

    private String suggestionId;
    private String groupId;
    private String status;
    private String groupStatus;
    private String roomId;
    private String curriculumId;
}
