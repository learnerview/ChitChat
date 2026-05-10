package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByTenantIdAndConversationIdOrderByCreatedAtAsc(String tenantId, String conversationId);

    Page<Message> findByTenantIdAndConversationId(String tenantId, String conversationId, Pageable pageable);

    Optional<Message> findByIdAndTenantId(String id, String tenantId);
    
    List<Message> findByTenantIdAndConversationIdAndContentContainingIgnoreCase(String tenantId, String conversationId, String query);
    
    List<Message> findByTenantIdAndConversationIdInAndContentContainingIgnoreCase(String tenantId, java.util.Collection<String> conversationIds, String query);
}
