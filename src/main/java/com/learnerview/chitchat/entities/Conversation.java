package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conversations")
public class Conversation {
    @Id
    private String id;
    
    private ConversationType type;
    
    /** Display name (null for DMs, required for groups) */
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;
    
    /** Unique @handle for public groups only (e.g., @developers) */
    @Indexed(unique = true, sparse = true)
    @Pattern(regexp = "^[a-z0-9_]{3,30}$", message = "Handle must be 3-30 lowercase letters, numbers, or underscores")
    private String handle;
    
    /** Group description (for groups only) */
    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
    
    /** Group avatar/icon URL */
    private String avatarUrl;
    
    /** Owner user ID (immutable, set at creation for groups) */
    private String ownerId;
    
    private LocalDateTime createdAt;
    
    /** UUID-based invite link for private groups */
    private String inviteLink;
    
    /** Cached member count (updated on join/leave) */
    @Builder.Default
    private int memberCount = 0;
    
    /** Only admins/owner can send messages if true (WhatsApp-like admin control) */
    @Builder.Default
    private boolean adminOnlyMessaging = false;
    
    /** For DM type: list of two usernames */
    @Builder.Default
    private List<String> users = new ArrayList<>();
}
