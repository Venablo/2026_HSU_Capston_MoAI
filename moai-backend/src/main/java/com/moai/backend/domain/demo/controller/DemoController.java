package com.moai.backend.domain.demo.controller;

import com.moai.backend.domain.demo.service.DemoResetService;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.common.ApiResponse;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연용 데모 모드 컨트롤러.
 * 로그아웃 자동 초기화의 백업용 수동 리셋 엔드포인트.
 * 시연 계정 화이트리스트(application.yaml moai.demo.cleanup-login-ids) 에 등록된 사용자만 호출 가능.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoResetService demoResetService;
    private final UserRepository userRepository;

    /**
     * 현재 로그인한 사용자의 모든 시연 데이터를 초기화한다.
     * 비시연 계정이 호출하면 403.
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!demoResetService.isDemoAccount(user.getLoginId())) {
            throw new AccessDeniedException("시연 계정만 호출 가능합니다.");
        }

        demoResetService.cleanupDemoData(user.getId());
        return ResponseEntity.ok(ApiResponse.success("데모 데이터 초기화 완료", null));
    }
}
