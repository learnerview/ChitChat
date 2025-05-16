package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.service.CacheService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class CacheServiceImpl implements CacheService {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    
    private static final long DEFAULT_TTL = TimeUnit.MINUTES.toMillis(30); // 30 minutes
    
    @Override
    public void cacheUser(String username, Object user) {
        put("user:" + username, user);
    }

    @Override
    public Object getCachedUser(String username) {
        return get("user:" + username);
    }

    @Override
    public void evictUser(String username) {
        evict("user:" + username);
    }

    @Override
    public void cacheConversation(String conversationId, Object conversation) {
        put("conversation:" + conversationId, conversation);
    }

    @Override
    public Object getCachedConversation(String conversationId) {
        return get("conversation:" + conversationId);
    }

    @Override
    public void evictConversation(String conversationId) {
        evict("conversation:" + conversationId);
    }

    @Override
    public void cacheMessages(String conversationId, List<?> messages) {
        put("messages:" + conversationId, messages);
    }

    @Override
    public List<?> getCachedMessages(String conversationId) {
        Object cached = get("messages:" + conversationId);
        return cached != null ? (List<?>) cached : null;
    }

    @Override
    public void evictMessages(String conversationId) {
        evict("messages:" + conversationId);
    }

    @Override
    public void cacheOnlineUsers(Set<String> onlineUsers) {
        put("online_users", onlineUsers);
    }

    @Override
    public Set<String> getCachedOnlineUsers() {
        Object cached = get("online_users");
        return cached != null ? (Set<String>) cached : new HashSet<>();
    }

    @Override
    public void evictOnlineUsers() {
        evict("online_users");
    }

    @Override
    public void cacheTypingUsers(String conversationId, Set<String> typingUsers) {
        put("typing:" + conversationId, typingUsers);
    }

    @Override
    public Set<String> getCachedTypingUsers(String conversationId) {
        Object cached = get("typing:" + conversationId);
        return cached != null ? (Set<String>) cached : new HashSet<>();
    }

    @Override
    public void evictTypingUsers(String conversationId) {
        evict("typing:" + conversationId);
    }

    @Override
    public void put(String key, Object value) {
        cache.put(key, value);
        timestamps.put(key, System.currentTimeMillis());
    }

    @Override
    public Object get(String key) {
        // Check if entry exists and is not expired
        Long timestamp = timestamps.get(key);
        if (timestamp == null) {
            return null;
        }
        
        if (System.currentTimeMillis() - timestamp > DEFAULT_TTL) {
            evict(key);
            return null;
        }
        
        return cache.get(key);
    }

    @Override
    public void evict(String key) {
        cache.remove(key);
        timestamps.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
        timestamps.clear();
    }
    
    // Cleanup expired entries
    public void cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        timestamps.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > DEFAULT_TTL) {
                cache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
