package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
    private String displayName;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password; // stored as bcrypt hash
    
    /** Profile picture URL */
    private String avatarUrl;
    
    /** User bio/status */
    @Size(max = 200, message = "Bio must be at most 200 characters")
    private String bio;
    
    /** IDs of users this user has blocked */
    @Builder.Default
    private Set<String> blockedUserIds = new HashSet<>();
    
    private LocalDateTime createdAt;
    private LocalDateTime lastSeen;
    private boolean online;
    
    @Builder.Default
    private Set<String> roles = new HashSet<>();
    
    /** Privacy Settings */
    @Builder.Default
    private boolean ghostMode = false;
    
    @Builder.Default
    private boolean showLastSeen = true;

    /** Check if this user has blocked another user */
    public boolean hasBlocked(String userId) {
        return blockedUserIds != null && blockedUserIds.contains(userId);
    }
}
