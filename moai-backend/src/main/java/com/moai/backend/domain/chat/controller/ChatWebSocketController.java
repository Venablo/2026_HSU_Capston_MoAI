package com.moai.backend.domain.chat.controller;

import com.moai.backend.domain.chat.dto.ChatSendRequestDto;
import com.moai.backend.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

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
}
