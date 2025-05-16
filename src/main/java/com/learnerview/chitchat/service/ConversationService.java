package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Conversation;
import java.util.List;

public interface ConversationService {
    
    Conversation createDM(String username1, String username2);
    
    Conversation createGroup(String name, String ownerUsername, boolean isPublic, String description, String handle);
    
    List<Conversation> getMyConversations(String username);
    
    Conversation getConversation(String id);
    
    List<Conversation> getPublicConversations(String username);
    
    Conversation joinPublicConversation(String conversationId, String username);
    
    void deleteConversation(String conversationId, String username);
    
    void leaveConversation(String conversationId, String username);
    
    Conversation updateSettings(String conversationId, boolean adminOnlyMessaging, String username);
    
    Conversation updateGroup(String conversationId, String name, String description, String username);
    
    String generateInviteLink(String conversationId, String username);
    
    void revokeInviteLink(String conversationId, String username);
    
    Conversation joinViaInviteLink(String inviteCode, String username);
    
    void togglePin(String conversationId, String username);
    
    void toggleMute(String conversationId, String username);
}
