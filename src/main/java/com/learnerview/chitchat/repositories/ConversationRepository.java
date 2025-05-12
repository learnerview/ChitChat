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
    
    List<Conversation> findByType(ConversationType type);
    
    List<Conversation> findByOwnerId(String ownerId);
    
    Optional<Conversation> findByHandle(String handle);
    
    boolean existsByHandle(String handle);
    
    List<Conversation> findByNameContainingIgnoreCaseOrHandleContainingIgnoreCase(String name, String handle);
    
    @Query("{'users': ?0}")
    List<Conversation> findByUsersContaining(String username);
    
    @Query("{'users': {$all: [?0, ?1]}, 'type': 'DM'}")
    Optional<Conversation> findDMByUsers(String user1, String user2);
}
