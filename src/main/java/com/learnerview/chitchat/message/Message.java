package com.learnerview.chitchat.message;

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
 * Ordering contract: {@code sequence} (per conversation), not timestamps.
 * Idempotency contract: {@code clientMessageId} is unique per (tenant, sender).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "conversation_sequence_unique",
            def = "{'conversationId': 1, 'sequence': 1}",
            unique = true,
            partialFilter = "{'sequence': {'$exists': true}}"),
    @CompoundIndex(name = "tenant_conversation_sequence",
            def = "{'tenantId': 1, 'conversationId': 1, 'sequence': -1}"),
    @CompoundIndex(name = "conversation_created_at",
            def = "{'conversationId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "client_message_id_unique",
            def = "{'tenantId': 1, 'senderId': 1, 'clientMessageId': 1}",
            unique = true,
            partialFilter = "{'clientMessageId': {'$exists': true}}")
})
public class Message {

    @Id
    private String id;

    private String tenantId;

    private String conversationId;

    private String senderId;

    /** Client-generated idempotency key; retries with the same key return the same message. */
    private String clientMessageId;

    private long sequence;

    private String content;

    private String replyToId;

    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

    /** Soft deletion: the record is kept, content is hidden by the API layer. */
    private LocalDateTime deletedAt;

    public boolean isEdited() {
        return editedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
