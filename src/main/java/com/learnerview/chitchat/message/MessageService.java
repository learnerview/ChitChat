package com.learnerview.chitchat.message;

import com.learnerview.chitchat.message.dto.EditMessageRequest;
import com.learnerview.chitchat.message.dto.MarkReadRequest;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.ReadStateResponse;
import com.learnerview.chitchat.message.dto.SendMessageRequest;

import java.util.List;

public interface MessageService {

    MessageResponse send(String conversationId, SendMessageRequest request);

    MessagePageResponse getMessages(String conversationId, Long before, Long after, Integer limit);

    void markAsRead(String conversationId, MarkReadRequest request);

    ReadStateResponse getReadState(String conversationId);

    MessageResponse editMessage(String messageId, EditMessageRequest request);

    void deleteMessage(String messageId);

    List<MessageResponse> searchInConversation(String conversationId, String query);

    List<MessageResponse> searchAll(String query);
}
