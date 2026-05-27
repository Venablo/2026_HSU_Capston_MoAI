package com.moai.backend.domain.demo.controller;

import com.moai.backend.domain.demo.dto.MockTemplateSeedRequestDto;
import com.moai.backend.domain.demo.dto.MockTemplateSeedResponseDto;
import com.moai.backend.domain.demo.service.DemoResetService;
import com.moai.backend.domain.demo.service.MockTemplateSeedService;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.common.ApiResponse;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final MockTemplateSeedService mockTemplateSeedService;
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

    /**
     * videoId + topic 만 받아서 mock_curriculum_templates + mock_transcript_templates 양쪽에
     * 적절한 값을 자동으로 채워 INSERT 한다.
     *
     * 동작 흐름:
     * - 자막은 SubtitleScraper(현재 provider 토글에 따라 ytdlp/supadata) 로 추출
     * - keywords/description 은 LLM 2회 호출로 자동 생성 (실제 학습실 생성 흐름과 동일 구조)
     * - weekNumber 는 기존 max + 1 로 자동 결정
     *
     * 시연 계정만 호출 가능 (비시연 계정은 403).
     */
    @PostMapping("/mock-templates/seed")
    public ResponseEntity<ApiResponse<MockTemplateSeedResponseDto>> seedMockTemplate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MockTemplateSeedRequestDto request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!demoResetService.isDemoAccount(user.getLoginId())) {
            throw new AccessDeniedException("시연 계정만 호출 가능합니다.");
        }

        MockTemplateSeedResponseDto result = mockTemplateSeedService.seed(request);
        return ResponseEntity.ok(ApiResponse.success("목업 템플릿 시드 완료", result));
    }
}
