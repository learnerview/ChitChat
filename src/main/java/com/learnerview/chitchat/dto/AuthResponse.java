package com.learnerview.chitchat.dto;

public record AuthResponse(
        String token,
        String type,
        String username,
        String displayName
) {
    public AuthResponse(String token, String username, String displayName) {
        this(token, "Bearer", username, displayName);
    }
}
