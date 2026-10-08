package com.learnerview.chitchat.message.dto;

import com.learnerview.chitchat.message.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        String id,
        String conversationId,
        String tenantId,
        String senderId,
        String clientMessageId,
        long sequence,
        String content,
        String replyToId,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        boolean edited,
        boolean deleted
) {
    public static MessageResponse from(Message message) {
        boolean deleted = message.isDeleted();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getTenantId(),
                message.getSenderId(),
                message.getClientMessageId(),
                message.getSequence(),
                deleted ? null : message.getContent(),
                message.getReplyToId(),
                message.getCreatedAt(),
                message.getEditedAt(),
                message.isEdited(),
                deleted);
    }
}
