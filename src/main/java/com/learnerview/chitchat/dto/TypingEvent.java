package com.learnerview.chitchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TypingEvent {
    private String conversationId;
    private String username;
    private boolean isTyping;
    private long timestamp;
    
    public TypingEvent(String conversationId, String username, boolean isTyping) {
        this.conversationId = conversationId;
        this.username = username;
        this.isTyping = isTyping;
        this.timestamp = System.currentTimeMillis();
    }
}
