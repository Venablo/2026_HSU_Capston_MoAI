package com.moai.backend.domain.learningroom.controller;

import com.moai.backend.domain.learningroom.dto.LearningRoomCreateRequestDto;
import com.moai.backend.domain.learningroom.dto.LearningRoomCreateResponseDto;
import com.moai.backend.domain.learningroom.service.LearningRoomService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms")
public class LearningRoomController {

    private final LearningRoomService learningRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<LearningRoomCreateResponseDto>> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LearningRoomCreateRequestDto requestDto) {

        LearningRoomCreateResponseDto responseDto =
                learningRoomService.createRoom(userDetails.getUsername(), requestDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "학습실 생성 성공", responseDto));
    }
}
