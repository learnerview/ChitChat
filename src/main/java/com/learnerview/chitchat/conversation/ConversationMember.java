package com.learnerview.chitchat.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Replaces the legacy {@code participantIds} set on Conversation and the
 * {@code readBy} set on Message. One document per (conversation, user).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conversation_members")
@CompoundIndexes({
    @CompoundIndex(name = "conversation_user_unique",
            def = "{'conversationId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "tenant_user",
            def = "{'tenantId': 1, 'userId': 1}")
})
public class ConversationMember {

    @Id
    private String id;

    private String tenantId;

    private String conversationId;

    private String userId;

    @Builder.Default
    private Role role = Role.MEMBER;

    private LocalDateTime joinedAt;

    /** Soft membership removal; a non-null value means the user has left. */
    private LocalDateTime leftAt;

    /** Read cursor: every message with sequence <= lastReadSequence is read. */
    @Builder.Default
    private long lastReadSequence = 0L;

    @Builder.Default
    private boolean muted = false;

    @Builder.Default
    private boolean archived = false;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private NotificationLevel notificationLevel = NotificationLevel.ALL;

    public boolean isActive() {
        return leftAt == null;
    }

    public enum Role {
        OWNER,
        ADMIN,
        MEMBER
    }

    public enum NotificationLevel {
        ALL,
        MENTIONS,
        NONE
    }
}
