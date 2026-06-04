package com.moai.backend.domain.learningroom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LearningRoomCreateRequestDto {

    @NotBlank(message = "학습 주제는 필수 입력 항목입니다.")
    private String subject;

    @NotBlank(message = "학습 수준은 필수 입력 항목입니다.")
    private String level;

    @NotNull(message = "학습 기간(주)은 필수 입력 항목입니다.")
    @Min(value = 1, message = "학습 기간은 최소 1주입니다.")
    @Max(value = 52, message = "학습 기간은 최대 52주입니다.")
    @JsonProperty("duration_weeks")
    private Short durationWeeks;

    @NotNull(message = "하루 학습 시간은 필수 입력 항목입니다.")
    @DecimalMin(value = "0.5", message = "하루 학습 시간은 최소 0.5시간입니다.")
    @DecimalMax(value = "12", message = "하루 학습 시간은 최대 12시간입니다.")
    @JsonProperty("hours_per_day")
    private BigDecimal hoursPerDay;
}
