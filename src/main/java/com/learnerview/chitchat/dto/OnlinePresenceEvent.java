package com.learnerview.chitchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnlinePresenceEvent {
    private String username;
    private boolean online;
    private long lastSeen;
    
    public OnlinePresenceEvent(String username, boolean online) {
        this.username = username;
        this.online = online;
        this.lastSeen = System.currentTimeMillis();
    }
}
