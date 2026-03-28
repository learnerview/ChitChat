package com.learnerview.chitchat.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        String username,
        String displayName,
        LocalDateTime createdAt
) {
}