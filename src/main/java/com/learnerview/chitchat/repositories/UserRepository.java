package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    Optional<User> findByUsername(String username);
    
    List<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String username, String displayName);
    
    boolean existsByUsername(String username);
    
    List<User> findByBlockedUserIdsContaining(String userId);
}
