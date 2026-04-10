package com.moai.backend.domain.quiz.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class LlmEssayGradingResult {

    private int score;
    private List<String> gainedKeywords;
    private List<String> weaknessKeywords;
    private String aiComment;
}
