package com.moai.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class ChatWebSocketEventListener {

    private static final String CHAT_DESTINATION_PREFIX = "/sub/chat/";

    private final ChatSessionRegistry chatSessionRegistry;

    private final ConcurrentHashMap<String, SubscriptionInfo> subscriptionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination == null || !destination.startsWith(CHAT_DESTINATION_PREFIX)) {
            return;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            return;
        }

        String groupId = destination.substring(CHAT_DESTINATION_PREFIX.length());
        String userId = principal.getName();
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String key = sessionId + ":" + subscriptionId;

        subscriptionMap.put(key, new SubscriptionInfo(groupId, userId));
        sessionSubscriptions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(key);

        chatSessionRegistry.register(groupId, userId);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String key = sessionId + ":" + subscriptionId;

        SubscriptionInfo info = subscriptionMap.remove(key);
        if (info != null) {
            chatSessionRegistry.remove(info.groupId(), info.userId());
            List<String> keys = sessionSubscriptions.get(sessionId);
            if (keys != null) {
                keys.remove(key);
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        List<String> keys = sessionSubscriptions.remove(sessionId);
        if (keys == null) {
            return;
        }

        for (String key : keys) {
            SubscriptionInfo info = subscriptionMap.remove(key);
            if (info != null) {
                chatSessionRegistry.remove(info.groupId(), info.userId());
            }
        }
    }

    private record SubscriptionInfo(String groupId, String userId) {}
}
