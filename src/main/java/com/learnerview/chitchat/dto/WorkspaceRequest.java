package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkspaceRequest {
    
    @NotBlank(message = "Workspace name is required")
    @Size(min = 1, max = 100, message = "Workspace name must be between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Workspace slug is required")
    @Size(min = 3, max = 50, message = "Workspace slug must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase alphanumeric or hyphen")
    private String slug;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
}
