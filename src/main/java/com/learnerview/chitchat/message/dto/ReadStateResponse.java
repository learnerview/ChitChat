package com.learnerview.chitchat.message.dto;

public record ReadStateResponse(
        String conversationId,
        long lastReadSequence,
        long latestSequence,
        long unreadCount
) {
}
