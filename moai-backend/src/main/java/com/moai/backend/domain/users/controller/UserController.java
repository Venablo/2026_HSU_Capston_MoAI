package com.moai.backend.domain.users.controller;

import com.moai.backend.domain.users.dto.*;
import com.moai.backend.domain.users.service.UserService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // GET /api/users/me — 프로필 전체 조회
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDto>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserProfileResponseDto profile = userService.getProfile(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success("프로필 조회 성공", profile));
    }

    // PATCH /api/users/me — 프로필 수정 (닉네임, 프로필 사진, 테마)
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserUpdateResponseDto>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserUpdateRequestDto requestDto) {

        UserUpdateResponseDto response = userService.updateProfile(userDetails.getUsername(), requestDto);

        return ResponseEntity.ok(ApiResponse.success("프로필 수정 성공", response));
    }

    // PATCH /api/users/me/keywords — 관심 키워드 수정
    @PatchMapping("/me/keywords")
    public ResponseEntity<ApiResponse<KeywordUpdateResponseDto>> updateKeywords(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody KeywordUpdateRequestDto requestDto) {

        KeywordUpdateResponseDto response = userService.updateKeywords(userDetails.getUsername(), requestDto);

        return ResponseEntity.ok(ApiResponse.success("관심 키워드 수정 성공", response));
    }

    // GET /api/users/me/learning-history — 학습실 이력 목록
    @GetMapping("/me/learning-history")
    public ResponseEntity<ApiResponse<List<LearningHistoryResponseDto>>> getLearningHistory(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<LearningHistoryResponseDto> history = userService.getLearningHistory(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success("학습실 이력 조회 성공", history));
    }

    // DELETE /api/users/me — 회원 탈퇴 (비밀번호 확인 후 논리 삭제)
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserDeleteRequestDto requestDto) {

        userService.deleteUser(userDetails.getUsername(), requestDto);

        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
    }
}
