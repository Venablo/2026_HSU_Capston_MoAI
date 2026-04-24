package com.moai.backend.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class LlmFinalQuizResult {

    @JsonProperty("quiz_title")
    private String quizTitle;

    @JsonProperty("total_score")
    private Integer totalScore;

    @JsonProperty("time_limit_minutes")
    private Integer timeLimitMinutes;

    @JsonProperty("study_guide")
    private String studyGuide;

    private List<QuestionData> questions;

    @Getter
    @NoArgsConstructor
    public static class QuestionData {
        private Integer order;
        private String question;

        @JsonProperty("related_keyword")
        private String relatedKeyword;

        @JsonProperty("max_length")
        private Short maxLength;

        @JsonProperty("max_score")
        private Integer maxScore;

        @JsonProperty("hint")
        private String tip;

        @JsonProperty("scoring_rubric")
        private String scoringRubric;

        @JsonProperty("sample_answer_keywords")
        private List<String> sampleAnswerKeywords;

        private String difficulty;
    }
}
