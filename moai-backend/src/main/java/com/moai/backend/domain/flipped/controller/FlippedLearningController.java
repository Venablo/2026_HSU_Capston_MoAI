package com.moai.backend.domain.flipped.controller;

import com.moai.backend.domain.flipped.dto.*;
import com.moai.backend.domain.flipped.service.FlippedLearningService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms/{roomId}/flipped")
public class FlippedLearningController {

    private final FlippedLearningService flippedLearningService;

    // SSE 타임아웃: 3분
    private static final long SSE_TIMEOUT = 3 * 60 * 1000L;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<FlippedStartResponseDto>> startSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @Valid @RequestBody FlippedStartRequestDto requestDto) {

        FlippedStartResponseDto responseDto =
                flippedLearningService.startSession(userDetails.getUsername(), roomId, requestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("거꾸로 학습 세션이 시작되었습니다.", responseDto));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @NotBlank @RequestParam("sessionId") String sessionId,
            @NotBlank @RequestParam("message") String message) {

        FlippedStreamRequestDto requestDto = FlippedStreamRequestDto.of(sessionId, message);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 비동기로 스트리밍 처리 — SseEmitter를 즉시 반환하고 백그라운드에서 토큰 전송
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        flippedLearningService.streamChat(
                userDetails.getUsername(), roomId, requestDto, emitter);

        return emitter;
    }

    @PostMapping("/end")
    public ResponseEntity<ApiResponse<FlippedEndResponseDto>> endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @Valid @RequestBody FlippedEndRequestDto requestDto) {

        FlippedEndResponseDto responseDto =
                flippedLearningService.endSession(userDetails.getUsername(), roomId, requestDto);

        return ResponseEntity.ok(ApiResponse.success("거꾸로 학습 세션이 종료되었습니다.", responseDto));
    }

    @GetMapping("/result/{sessionId}")
    public ResponseEntity<ApiResponse<FlippedResultResponseDto>> getResult(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String sessionId) {

        FlippedResultResponseDto responseDto =
                flippedLearningService.getResult(userDetails.getUsername(), roomId, sessionId);

        return ResponseEntity.ok(ApiResponse.success("거꾸로 학습 평가 결과 조회 완료", responseDto));
    }
}
