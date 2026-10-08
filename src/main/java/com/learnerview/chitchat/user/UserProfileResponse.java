package com.learnerview.chitchat.user;

import java.time.LocalDateTime;

public record UserProfileResponse(
        String id,
        String username,
        String displayName,
        UserStatus status,
        LocalDateTime lastSeenAt,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt()
        );
    }
}
