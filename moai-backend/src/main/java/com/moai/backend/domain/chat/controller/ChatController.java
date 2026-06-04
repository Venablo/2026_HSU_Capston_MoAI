package com.moai.backend.domain.chat.controller;

import com.moai.backend.domain.chat.dto.ChatMessageResponseDto;
import com.moai.backend.domain.chat.service.ChatService;
import com.moai.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-groups")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{groupId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponseDto>>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<ChatMessageResponseDto> messages =
                chatService.getMessages(userDetails.getUsername(), groupId, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success("채팅 이력 조회 성공", messages));
    }
}
