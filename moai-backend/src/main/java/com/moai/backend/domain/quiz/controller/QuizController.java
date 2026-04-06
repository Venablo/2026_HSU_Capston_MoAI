package com.moai.backend.domain.quiz.controller;

import com.moai.backend.domain.quiz.dto.InstantQuizResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptRequestDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptResponseDto;
import com.moai.backend.domain.quiz.service.QuizService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;;

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

    @GetMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/instant")
    public ResponseEntity<ApiResponse<InstantQuizResponseDto>> getInstantQuiz(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        InstantQuizResponseDto responseDto =
                quizService.getInstantQuiz(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("돌발 퀴즈 조회 성공", responseDto));
    }

    @PostMapping("/api/quiz-attempts")
    public ResponseEntity<ApiResponse<QuizAttemptResponseDto>> submitQuizAttempt(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuizAttemptRequestDto requestDto) {

        QuizAttemptResponseDto responseDto =
                quizService.submitQuizAttempt(userDetails.getUsername(), requestDto);

        return ResponseEntity.ok(ApiResponse.success("퀴즈 제출 완료", responseDto));
    }
}
