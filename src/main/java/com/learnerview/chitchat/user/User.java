package com.learnerview.chitchat.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
@CompoundIndexes({
    @CompoundIndex(name = "username_unique", def = "{'username': 1}", unique = true)
})
public class User {

    @Id
    private String id;

    private String username;

    private String displayName;

    /** Never exposed through any DTO or API response. */
    private String passwordHash;

    private String externalUserId;

    /** Tokens issued before this instant are rejected (password-change invalidation). */
    private java.time.LocalDateTime passwordChangedAt;

    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastSeenAt;
}
