package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "tenants")
public class Tenant {
    @Id
    private String id;

    @NotBlank(message = "Workspace name is required")
    @Size(min = 1, max = 100, message = "Workspace name must be between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Workspace slug is required")
    @Indexed(unique = true)
    @Size(min = 3, max = 50, message = "Workspace slug must be between 3 and 50 characters")
    private String slug; // URL-friendly identifier (e.g., "acme-corp")

    private String ownerId; // User ID of the workspace owner

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private LocalDateTime createdAt;

    @Builder.Default
    private boolean active = true;
}
