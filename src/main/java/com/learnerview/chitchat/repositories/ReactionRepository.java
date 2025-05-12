package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Reaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends MongoRepository<Reaction, String> {
    
    List<Reaction> findByMessageId(String messageId);
    
    Optional<Reaction> findByMessageIdAndUserIdAndEmoji(String messageId, String userId, String emoji);
    
    void deleteByMessageIdAndUserIdAndEmoji(String messageId, String userId, String emoji);
    
    List<Reaction> findByUserId(String userId);
}
