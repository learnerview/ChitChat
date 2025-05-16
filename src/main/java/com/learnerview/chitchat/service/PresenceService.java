package com.learnerview.chitchat.service;

import java.util.Set;

public interface PresenceService {
    
    void setUserOnline(String username);
    
    void setUserOffline(String username);
    
    boolean isUserOnline(String username);
    
    Set<String> getOnlineUsers();
    
    void setTyping(String conversationId, String username, boolean isTyping);
    
    Set<String> getTypingUsers(String conversationId);
    
    void clearTyping(String conversationId, String username);
    
    void cleanupExpiredTyping();
}
