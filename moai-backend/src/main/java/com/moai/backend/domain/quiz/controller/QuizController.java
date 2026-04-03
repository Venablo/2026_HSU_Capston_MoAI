package com.moai.backend.domain.quiz.controller;

import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.service.QuizService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-attempts")
    public ResponseEntity<ApiResponse<QuizAttemptListResponseDto>> getQuizAttempts(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        QuizAttemptListResponseDto responseDto =
                quizService.getQuizAttempts(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("퀴즈 응시 이력 조회 성공", responseDto));
    }

    @GetMapping("/api/quiz-attempts/{attemptId}")
    public ResponseEntity<ApiResponse<QuizAttemptDetailResponseDto>> getQuizAttemptDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String attemptId) {

        QuizAttemptDetailResponseDto responseDto =
                quizService.getQuizAttemptDetail(userDetails.getUsername(), attemptId);

        return ResponseEntity.ok(ApiResponse.success("퀴즈 상세 조회 성공", responseDto));
    }
}
