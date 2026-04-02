package com.moai.backend.domain.users.controller;

import com.moai.backend.domain.users.dto.UserProfileResponseDto;
import com.moai.backend.domain.users.service.UserService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDto>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserProfileResponseDto profile = userService.getProfile(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success(200, "프로필 조회 성공", profile));
    }
}
