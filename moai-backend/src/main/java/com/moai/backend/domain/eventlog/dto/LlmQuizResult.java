package com.moai.backend.domain.eventlog.dto;

import com.moai.backend.domain.quiz.entity.QuizOption;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class LlmQuizResult {

    private String question;
    private List<QuizOption> options;
    private String answer;
    private String relatedKeyword;
}
