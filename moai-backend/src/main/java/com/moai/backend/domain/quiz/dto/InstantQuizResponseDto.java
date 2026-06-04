package com.moai.backend.domain.quiz.dto;

import com.moai.backend.domain.quiz.entity.QuizOption;
import com.moai.backend.domain.quiz.entity.QuizQuestion;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class InstantQuizResponseDto {

    private String questionId;
    private String quizId;
    private String questionType;
    private String question;
    private List<QuizOption> options;
    private Short timeLimitSec;
    private String relatedKeyword;

    public static InstantQuizResponseDto from(QuizQuestion q) {
        return InstantQuizResponseDto.builder()
                .questionId(q.getId())
                .quizId(q.getQuiz().getId())
                .questionType(q.getQuestionType())
                .question(q.getQuestion())
                .options(q.getOptions())
                .timeLimitSec(q.getTimeLimitSec())
                .relatedKeyword(q.getRelatedKeyword())
                .build();
    }
}
