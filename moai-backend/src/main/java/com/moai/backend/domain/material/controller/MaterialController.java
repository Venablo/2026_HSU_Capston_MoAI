package com.moai.backend.domain.material.controller;

import com.moai.backend.domain.material.dto.MaterialDetailResponseDto;
import com.moai.backend.domain.material.dto.MaterialListResponseDto;
import com.moai.backend.domain.material.service.MaterialService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms/{roomId}/materials")
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialListResponseDto.MaterialSummary>>> getMaterials(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {

        MaterialListResponseDto responseDto =
                materialService.getMaterials(userDetails.getUsername(), roomId);

        return ResponseEntity.ok(ApiResponse.success("요약 자료 목록 조회 성공", responseDto.getMaterials()));
    }

    @GetMapping("/{materialId}")
    public ResponseEntity<ApiResponse<MaterialDetailResponseDto>> getMaterialDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @PathVariable String materialId) {

        MaterialDetailResponseDto responseDto =
                materialService.getMaterialDetail(userDetails.getUsername(), roomId, materialId);

        return ResponseEntity.ok(ApiResponse.success("요약 자료 상세 조회 성공", responseDto));
    }
}
