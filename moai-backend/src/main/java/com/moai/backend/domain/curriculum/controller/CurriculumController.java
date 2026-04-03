package com.moai.backend.domain.curriculum.controller;

import com.moai.backend.domain.curriculum.dto.*;
import com.moai.backend.domain.curriculum.service.CurriculumService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms/{roomId}/curriculum")
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping
    public ResponseEntity<ApiResponse<CurriculumListResponseDto>> getCurriculumList(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {

        CurriculumListResponseDto responseDto =
                curriculumService.getCurriculumList(userDetails.getUsername(), roomId);

        return ResponseEntity.ok(ApiResponse.success("주차 목록 조회 성공", responseDto));
    }

    @GetMapping("/{weekId}")
    public ResponseEntity<ApiResponse<CurriculumDetailResponseDto>> getCurriculumDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        CurriculumDetailResponseDto responseDto =
                curriculumService.getCurriculumDetail(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("주차 상세 조회 성공", responseDto));
    }

    @PatchMapping("/{weekId}/progress")
    public ResponseEntity<ApiResponse<Void>> updateProgress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId,
            @Valid @RequestBody ProgressUpdateRequestDto requestDto) {

        curriculumService.updateProgress(userDetails.getUsername(), roomId, weekId, requestDto);

        return ResponseEntity.ok(ApiResponse.success("진척도 업데이트 성공"));
    }

    @GetMapping("/{weekId}/recommended-videos")
    public ResponseEntity<ApiResponse<RecommendedVideoListResponseDto>> getRecommendedVideos(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        RecommendedVideoListResponseDto responseDto =
                curriculumService.getRecommendedVideos(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("추천 영상 조회 성공", responseDto));
    }
}
