package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "invite_links")
@CompoundIndex(def = "{'tenantId': 1, 'createdAt': -1}")
public class InviteLink {
    @Id
    private String id;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Creator user ID is required")
    private String createdBy; // User ID of who created the invite

    @NotBlank(message = "Token is required")
    @Indexed(unique = true)
    private String token; // Unique invite token

    private LocalDateTime expiresAt; // When the invite expires

    private LocalDateTime createdAt;

    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // No expiration = never expires
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
