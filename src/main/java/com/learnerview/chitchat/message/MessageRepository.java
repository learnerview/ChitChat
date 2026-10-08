package com.learnerview.chitchat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Optional<Message> findByIdAndTenantId(String id, String tenantId);

    Optional<Message> findByTenantIdAndSenderIdAndClientMessageId(String tenantId, String senderId, String clientMessageId);

    /**
     * Cursor page of messages strictly older than {@code sequence}, newest first.
     * Pass {@link Long#MAX_VALUE} to load the most recent page.
     */
    List<Message> findByTenantIdAndConversationIdAndSequenceLessThan(
            String tenantId, String conversationId, long sequence, Pageable pageable);

    /** Cursor page of messages strictly newer than {@code sequence}, oldest first. */
    List<Message> findByTenantIdAndConversationIdAndSequenceGreaterThan(
            String tenantId, String conversationId, long sequence, Pageable pageable);

    List<Message> findByTenantIdAndConversationIdAndDeletedAtIsNullAndContentContainingIgnoreCase(
            String tenantId, String conversationId, String query, Pageable pageable);

    List<Message> findByTenantIdAndConversationIdInAndDeletedAtIsNullAndContentContainingIgnoreCase(
            String tenantId, Collection<String> conversationIds, String query, Pageable pageable);
}
