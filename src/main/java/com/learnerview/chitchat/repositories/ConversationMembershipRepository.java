package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.ConversationMembership;
import com.learnerview.chitchat.entities.MembershipStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMembershipRepository extends MongoRepository<ConversationMembership, String> {
    
    List<ConversationMembership> findByConversationId(String conversationId);
    
    Optional<ConversationMembership> findByConversationIdAndUsername(String conversationId, String username);
    
    List<ConversationMembership> findByUsernameAndStatus(String username, MembershipStatus status);
    
    List<ConversationMembership> findByUsername(String username);
    
    List<ConversationMembership> findByConversationIdAndStatus(String conversationId, MembershipStatus status);
    
    void deleteByConversationIdAndUsername(String conversationId, String username);
}
