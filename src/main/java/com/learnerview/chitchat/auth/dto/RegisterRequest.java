package com.learnerview.chitchat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must be alphanumeric or underscore")
        String username,

        @NotBlank(message = "Display name is required")
        @Size(min = 1, max = 80, message = "Display name must be between 1 and 80 characters")
        String displayName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password
) {
}
