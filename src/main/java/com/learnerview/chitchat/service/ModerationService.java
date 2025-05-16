package com.learnerview.chitchat.service;

import java.util.List;

public interface ModerationService {
    
    boolean containsProfanity(String content);
    
    boolean containsSpam(String content);
    
    boolean containsPersonalInfo(String content);
    
    boolean containsInappropriateContent(String content);
    
    String filterContent(String content);
    
    void flagMessage(String messageId, String reason);
    
    List<String> getFlaggedMessages();
    
    void moderateMessage(String messageId, boolean approve);
    
    void banUser(String username, String reason);
    
    void unbanUser(String username);
    
    boolean isUserBanned(String username);
    
    void reportContent(String type, String contentId, String reporterUsername, String reason);
    
    List<String> getReports();
}
