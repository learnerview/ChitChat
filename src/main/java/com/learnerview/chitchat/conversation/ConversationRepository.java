package com.learnerview.chitchat.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByIdAndTenantId(String id, String tenantId);

    Optional<Conversation> findByTenantIdAndDirectKey(String tenantId, String directKey);

    List<Conversation> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    List<Conversation> findByTenantIdAndType(String tenantId, ConversationType type);
}
