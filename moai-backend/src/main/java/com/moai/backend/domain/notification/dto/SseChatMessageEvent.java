package com.moai.backend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SseChatMessageEvent {

    private String type;
    private String message;
    private String groupId;
    private SenderInfo sender;
    private String preview;

    @Getter
    @AllArgsConstructor
    public static class SenderInfo {
        private String nickname;
        private String profileImageUrl;
    }
}
