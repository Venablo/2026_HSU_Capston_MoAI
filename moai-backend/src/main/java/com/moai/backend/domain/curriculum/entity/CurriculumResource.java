package com.moai.backend.domain.curriculum.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurriculumResource {

    private String type;

    @JsonProperty("video_id")
    private String videoId;

    private String title;

    private String url;

    private String size;

    @JsonProperty("duration_sec")
    private Long durationSec;

    @JsonProperty("view_count")
    private Long viewCount;
}