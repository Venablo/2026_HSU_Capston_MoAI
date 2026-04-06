package com.moai.backend.domain.eventlog.controller;

import com.moai.backend.domain.eventlog.dto.EventRequestDto;
import com.moai.backend.domain.eventlog.dto.EventResponseDto;
import com.moai.backend.domain.eventlog.service.EventLogService;
import com.moai.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning-rooms/{roomId}/events")
public class EventLogController {

    private final EventLogService eventLogService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponseDto>> sendEvent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @Valid @RequestBody EventRequestDto requestDto) {

        EventResponseDto responseDto =
                eventLogService.processEvent(userDetails.getUsername(), roomId, requestDto);

        return ResponseEntity.ok(ApiResponse.success("이벤트 처리 완료", responseDto));
    }
}
