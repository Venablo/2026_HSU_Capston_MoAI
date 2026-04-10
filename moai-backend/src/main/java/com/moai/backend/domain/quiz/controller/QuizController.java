package com.moai.backend.domain.quiz.controller;

import com.moai.backend.domain.quiz.dto.FinalQuizResponseDto;
import com.moai.backend.domain.quiz.dto.FinalQuizSubmitRequestDto;
import com.moai.backend.domain.quiz.dto.FinalQuizSubmitResponseDto;
import com.moai.backend.domain.quiz.dto.InstantQuizResponseDto;
import com.moai.backend.domain.quiz.dto.QuizReportResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptRequestDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptResponseDto;
import com.moai.backend.domain.quiz.service.QuizService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @GetMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final")
    public ResponseEntity<ApiResponse<FinalQuizResponseDto>> getFinalQuiz(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        FinalQuizResponseDto responseDto =
                quizService.getFinalQuiz(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("파이널 퀴즈 조회 성공", responseDto));
    }

    @PostMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/quizzes/final/submit")
    public ResponseEntity<ApiResponse<FinalQuizSubmitResponseDto>> submitFinalQuiz(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId,
            @Valid @RequestBody FinalQuizSubmitRequestDto requestDto) {

        FinalQuizSubmitResponseDto responseDto =
                quizService.submitFinalQuiz(userDetails.getUsername(), roomId, weekId, requestDto);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("파이널 퀴즈 제출 완료 — AI 분석 진행 중", responseDto));
    }

    @GetMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/quiz-report")
    public ResponseEntity<ApiResponse<QuizReportResponseDto>> getQuizReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        QuizReportResponseDto responseDto =
                quizService.getQuizReport(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("퀴즈 리포트 조회 성공", responseDto));
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
