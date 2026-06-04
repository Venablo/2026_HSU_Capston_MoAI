package com.moai.backend.domain.study.controller;

import com.moai.backend.domain.study.dto.MatchRequestDto;
import com.moai.backend.domain.study.dto.MatchRequestResponseDto;
import com.moai.backend.domain.study.service.MatchingService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms/{roomId}/match")
public class MatchingController {

    private final MatchingService matchingService;

    @PostMapping
    public ResponseEntity<ApiResponse<MatchRequestResponseDto>> requestMatch(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @Valid @RequestBody MatchRequestDto requestDto) {

        matchingService.requestMatch(userDetails.getUsername(), roomId, requestDto);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("매칭 요청이 접수되었습니다.",
                        new MatchRequestResponseDto("searching")));
    }
}
