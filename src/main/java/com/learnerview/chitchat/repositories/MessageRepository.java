package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    
    List<Message> findByConversationIdOrderByTimeStampAsc(String conversationId);
    
    List<Message> findByConversationIdAndSenderOrderByTimeStampDesc(String conversationId, String sender);
    
    List<Message> findByConversationIdAndSenderNotOrderByTimeStampDesc(String conversationId, String sender);
    
    Page<Message> findByConversationId(String conversationId, Pageable pageable);
    
    @Query("{'conversationId': ?0, 'deletedBy': {'$ne': ?1}}")
    List<Message> findByConversationIdAndNotDeletedBy(String conversationId, String username);
    
    @Query("{'content': {'$regex': ?0, '$options': 'i'}}")
    List<Message> searchByContent(String query);
}
