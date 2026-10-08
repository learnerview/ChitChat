package com.learnerview.chitchat.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends MongoRepository<ConversationMember, String> {

    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByTenantIdAndUserId(String tenantId, String userId);

    List<ConversationMember> findByTenantIdAndUserIdAndLeftAtIsNull(String tenantId, String userId);

    List<ConversationMember> findByConversationId(String conversationId);

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(String conversationId);

    long countByConversationIdAndLeftAtIsNull(String conversationId);

    List<ConversationMember> findByConversationIdInAndUserId(Collection<String> conversationIds, String userId);
}
