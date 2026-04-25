package com.moai.backend.domain.quiz.dto;

import com.moai.backend.domain.quiz.entity.QuizAttempt;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QuizAttemptListResponseDto {

    private String attemptId;
    private String questionTitle;
    private Boolean isCorrect;
    private String videoSegment;
    private LocalDateTime attemptedAt;

    public static QuizAttemptListResponseDto from(QuizAttempt attempt) {
        return QuizAttemptListResponseDto.builder()
                .attemptId(attempt.getId())
                .questionTitle(deriveTitle(attempt))
                .isCorrect(attempt.getIsCorrect())
                .videoSegment(formatSegment(attempt.getQuiz().getRewindToSec()))
                .attemptedAt(attempt.getAttemptedAt())
                .build();
    }

    private static String deriveTitle(QuizAttempt attempt) {
        String keyword = attempt.getQuestion().getRelatedKeyword();
        if (keyword != null && !keyword.isBlank()) return keyword;
        String q = attempt.getQuestion().getQuestion();
        return q != null && q.length() > 50 ? q.substring(0, 50) + "..." : q;
    }

    private static String formatSegment(Integer sec) {
        if (sec == null) return null;
        return String.format("영상 구간 %d:%02d", sec / 60, sec % 60);
    }
}
