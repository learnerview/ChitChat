package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Message;

import java.util.List;
import java.util.Map;

public interface MessageService {
    Message sendMessage(String conversationId, String sender, String content);

    Message sendMessage(String conversationId, String sender, String content, String replyToId);

    List<Message> getMessageHistory(String conversationId, String viewerUsername);

    Map<String, Object> getMessageHistoryPaginated(String conversationId, String viewerUsername, int page, int size);

    void markAsRead(String conversationId, String username);

    Message editMessage(String messageId, String editor, String updatedContent);

    void deleteMessage(String messageId, String requester);

    List<Message> searchMessages(String conversationId, String username, String query);

    List<Message> searchAllMyMessages(String username, String query);
}