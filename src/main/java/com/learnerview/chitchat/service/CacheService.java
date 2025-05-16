package com.learnerview.chitchat.service;

import java.util.List;
import java.util.Set;

public interface CacheService {
    
    // User caching
    void cacheUser(String username, Object user);
    
    Object getCachedUser(String username);
    
    void evictUser(String username);
    
    // Conversation caching
    void cacheConversation(String conversationId, Object conversation);
    
    Object getCachedConversation(String conversationId);
    
    void evictConversation(String conversationId);
    
    // Message caching
    void cacheMessages(String conversationId, List<?> messages);
    
    List<?> getCachedMessages(String conversationId);
    
    void evictMessages(String conversationId);
    
    // Online users caching
    void cacheOnlineUsers(Set<String> onlineUsers);
    
    Set<String> getCachedOnlineUsers();
    
    void evictOnlineUsers();
    
    // Typing indicators caching
    void cacheTypingUsers(String conversationId, Set<String> typingUsers);
    
    Set<String> getCachedTypingUsers(String conversationId);
    
    void evictTypingUsers(String conversationId);
    
    // Generic caching
    void put(String key, Object value);
    
    Object get(String key);
    
    void evict(String key);
    
    void clear();
}
