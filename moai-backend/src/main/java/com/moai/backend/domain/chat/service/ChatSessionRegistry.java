package com.moai.backend.domain.chat.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionRegistry {

    private final ConcurrentHashMap<String, Set<String>> groupConnections = new ConcurrentHashMap<>();

    public void register(String groupId, String userId) {
        groupConnections.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public void remove(String groupId, String userId) {
        Set<String> users = groupConnections.get(groupId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                groupConnections.remove(groupId);
            }
        }
    }

    public boolean isConnected(String groupId, String userId) {
        Set<String> users = groupConnections.get(groupId);
        return users != null && users.contains(userId);
    }
}
