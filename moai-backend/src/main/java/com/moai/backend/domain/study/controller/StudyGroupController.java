package com.moai.backend.domain.study.controller;

import com.moai.backend.domain.study.dto.StudyGroupDetailResponseDto;
import com.moai.backend.domain.study.dto.SuggestionAcceptResponseDto;
import com.moai.backend.domain.study.dto.SuggestionListResponseDto;
import com.moai.backend.domain.study.dto.SuggestionRejectResponseDto;
import com.moai.backend.domain.study.service.StudyGroupService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-groups")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<SuggestionListResponseDto>>> getSuggestions(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<SuggestionListResponseDto> responseDto =
                studyGroupService.getSuggestions(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success("매칭 제안 목록 조회 완료", responseDto));
    }

    @PostMapping("/suggestions/{id}/accept")
    public ResponseEntity<ApiResponse<SuggestionAcceptResponseDto>> acceptSuggestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        SuggestionAcceptResponseDto responseDto =
                studyGroupService.acceptSuggestion(userDetails.getUsername(), id);

        return ResponseEntity.ok(ApiResponse.success("스터디 제안을 수락했습니다.", responseDto));
    }

    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<ApiResponse<SuggestionRejectResponseDto>> rejectSuggestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {

        SuggestionRejectResponseDto responseDto =
                studyGroupService.rejectSuggestion(userDetails.getUsername(), id);

        return ResponseEntity.ok(ApiResponse.success("스터디 제안을 거절했습니다.", responseDto));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<StudyGroupDetailResponseDto>> getGroupDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupId) {

        StudyGroupDetailResponseDto responseDto =
                studyGroupService.getGroupDetail(userDetails.getUsername(), groupId);

        return ResponseEntity.ok(ApiResponse.success("스터디 그룹 상세 조회 완료", responseDto));
    }
}
