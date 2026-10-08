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
 * Durable conversation state. Membership lives in {@link ConversationMember},
 * ordering lives in {@link #lastMessageSequence} - timestamps are metadata only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_directkey_unique",
            def = "{'tenantId': 1, 'directKey': 1}",
            unique = true,
            partialFilter = "{'directKey': {'$exists': true}}"),
    @CompoundIndex(name = "tenant_updated_at",
            def = "{'tenantId': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "tenant_status",
            def = "{'tenantId': 1, 'status': 1}")
})
public class Conversation {

    @Id
    private String id;

    private String tenantId;

    private ConversationType type;

    private String name;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Sorted "userA:userB" pair for DM conversations; uniqueness is enforced per tenant. */
    private String directKey;

    /** Monotonically increasing per-conversation ordering primitive. */
    @Builder.Default
    private long lastMessageSequence = 0L;

    private LocalDateTime lastMessageAt;

    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    public static String directKey(String userA, String userB) {
        if (userA == null || userB == null || userA.equals(userB)) {
            throw new IllegalArgumentException("A direct conversation requires two distinct users");
        }
        return userA.compareTo(userB) <= 0 ? userA + ":" + userB : userB + ":" + userA;
    }
}
