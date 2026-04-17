package com.moai.backend.domain.chat.dto;

import com.moai.backend.domain.chat.entity.StudyMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponseDto {

    private String messageId;
    private String senderType;
    private String senderId;
    private String senderNickname;
    private String content;
    private Boolean isAiCorrection;
    private LocalDateTime sentAt;

    public static ChatMessageResponseDto from(StudyMessage message) {
        boolean isAi = "ai".equals(message.getSenderType());

        return ChatMessageResponseDto.builder()
                .messageId(message.getId())
                .senderType(message.getSenderType())
                .senderId(isAi ? null : message.getSender().getId())
                .senderNickname(isAi ? "AI 튜터" : message.getSender().getNickname())
                .content(message.getContent())
                .isAiCorrection(message.getIsAiCorrection())
                .sentAt(message.getSentAt())
                .build();
    }
}
