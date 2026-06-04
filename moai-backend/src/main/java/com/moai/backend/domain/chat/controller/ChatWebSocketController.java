package com.moai.backend.domain.chat.controller;

import com.moai.backend.domain.chat.dto.ChatSendRequestDto;
import com.moai.backend.domain.chat.service.ChatService;
import com.moai.backend.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat/{groupId}")
    public void handleMessage(@DestinationVariable String groupId,
                              ChatSendRequestDto request,
                              Principal principal) {
        chatService.sendMessage(groupId, principal.getName(), request.getContent());
    }

    // WebSocket 메시지 전송 시 발생한 예외를 클라이언트에게 에러 메시지로 반환
    @MessageExceptionHandler(CustomException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleCustomException(CustomException ex) {
        return Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage()
        );
    }
}
