package com.learnerview.chitchat.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateGroupConversationRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        Set<String> memberIds
) {
}
