package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.service.PresenceService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class PresenceServiceImpl implements PresenceService {

    private final Set<String> onlineUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Set<String>> typingUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> typingTimestamps = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public PresenceServiceImpl() {
        // Clean up expired typing indicators every 10 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTyping, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void setUserOnline(String username) {
        onlineUsers.add(username);
    }

    @Override
    public void setUserOffline(String username) {
        onlineUsers.remove(username);
        // Clear typing indicators for offline user
        typingUsers.values().forEach(typingSet -> typingSet.remove(username));
    }

    @Override
    public boolean isUserOnline(String username) {
        return onlineUsers.contains(username);
    }

    @Override
    public Set<String> getOnlineUsers() {
        return new HashSet<>(onlineUsers);
    }

    @Override
    public void setTyping(String conversationId, String username, boolean isTyping) {
        if (isTyping) {
            typingUsers.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(username);
            typingTimestamps.put(conversationId + ":" + username, System.currentTimeMillis());
        } else {
            clearTyping(conversationId, username);
        }
    }

    @Override
    public Set<String> getTypingUsers(String conversationId) {
        Set<String> typing = typingUsers.get(conversationId);
        return typing != null ? new HashSet<>(typing) : new HashSet<>();
    }

    @Override
    public void clearTyping(String conversationId, String username) {
        Set<String> typing = typingUsers.get(conversationId);
        if (typing != null) {
            typing.remove(username);
            typingTimestamps.remove(conversationId + ":" + username);
        }
    }

    @Override
    public void cleanupExpiredTyping() {
        long currentTime = System.currentTimeMillis();
        long expirationTime = 5000; // 5 seconds
        
        typingTimestamps.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > expirationTime) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    String conversationId = parts[0];
                    String username = parts[1];
                    clearTyping(conversationId, username);
                }
                return true;
            }
            return false;
        });
    }
}
