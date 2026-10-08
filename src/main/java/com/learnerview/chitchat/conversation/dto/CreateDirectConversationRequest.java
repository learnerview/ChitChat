package com.learnerview.chitchat.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDirectConversationRequest(
        @NotBlank(message = "userId is required")
        String userId
) {
}
