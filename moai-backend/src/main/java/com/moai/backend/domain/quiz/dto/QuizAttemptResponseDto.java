package com.moai.backend.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizAttemptResponseDto {

    private String attemptId;

    // primitive boolean + Lombok @Getter 조합은 isCorrect() getter 를 생성하고
    // Jackson 이 'is' 접두사를 떼어 키를 'correct' 로 직렬화하므로,
    // 프론트 계약(isCorrect) 을 유지하기 위해 키 이름을 명시한다.
    @JsonProperty("isCorrect")
    private boolean isCorrect;

    private String correctAnswer;
    private String aiExplanation;
    private String relatedVideoId;
    private Integer relatedTimestamp;
    private Integer rewindToSec;
}
