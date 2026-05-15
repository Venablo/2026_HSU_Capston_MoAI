package com.moai.backend.domain.keyword.controller;

import com.moai.backend.domain.keyword.dto.KeywordListResponseDto;
import com.moai.backend.domain.keyword.service.KeywordService;
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
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping("/api/learning-rooms/{roomId}/keywords")
    public ResponseEntity<ApiResponse<KeywordListResponseDto>> getKeywords(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {

        KeywordListResponseDto responseDto =
                keywordService.getKeywords(userDetails.getUsername(), roomId);

        return ResponseEntity.ok(ApiResponse.success("키워드 목록 조회 성공", responseDto));
    }

    @GetMapping("/api/learning-rooms/{roomId}/curriculum/{weekId}/keywords")
    public ResponseEntity<ApiResponse<KeywordListResponseDto>> getKeywordsByCurriculum(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String weekId) {

        KeywordListResponseDto responseDto =
                keywordService.getKeywordsByCurriculum(userDetails.getUsername(), roomId, weekId);

        return ResponseEntity.ok(ApiResponse.success("주차별 키워드 목록 조회 성공", responseDto));
    }
}
