package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.entities.ConversationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByTenantIdAndType(String tenantId, ConversationType type);

    Optional<Conversation> findByIdAndTenantId(String id, String tenantId);

    @Query("{'tenantId': ?0, 'participantIds': ?1}")
    List<Conversation> findByTenantIdAndParticipantIdsContaining(String tenantId, String userId);

    @Query("{'tenantId': ?0, 'participantIds': {$all: [?1, ?2]}, 'type': 'DM'}")
    Optional<Conversation> findDirectConversation(String tenantId, String user1Id, String user2Id);
}
