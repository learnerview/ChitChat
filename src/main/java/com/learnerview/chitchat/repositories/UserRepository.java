package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByTenantIdAndUsername(String tenantId, String username);

    List<User> findByTenantIdAndUsernameContainingIgnoreCaseOrTenantIdAndDisplayNameContainingIgnoreCase(
            String tenantIdForUsername,
            String username,
            String tenantIdForDisplayName,
            String displayName
    );

    boolean existsByTenantIdAndUsername(String tenantId, String username);
}
