package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Message;
import com.learnerview.chitchat.dto.PaginatedResponse;
import java.util.List;

public interface MessageService {
    
    Message sendMessage(String conversationId, String sender, String content);
    
    Message sendMessage(String conversationId, String sender, String content, String replyToId);
    
    Message sendMessage(String conversationId, String sender, String content, String replyToId, List<com.learnerview.chitchat.entities.Attachment> attachments);
    
    List<Message> getMessageHistory(String conversationId, String viewerUsername);
    
    PaginatedResponse<Message> getMessageHistoryPaginated(String conversationId, String viewerUsername, int page, int size);
    
    void markAsRead(String conversationId, String username);
    
    void deleteMessage(String messageId, String username, boolean deleteForEveryone);
    
    Message editMessage(String messageId, String newContent, String username);
    
    Message forwardMessage(String messageId, String toConversationId, String sender);
    
    Message getMessage(String id);
}
