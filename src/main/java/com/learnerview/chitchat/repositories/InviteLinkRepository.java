package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.InviteLink;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InviteLinkRepository extends MongoRepository<InviteLink, String> {
    Optional<InviteLink> findByToken(String token);
    
    List<InviteLink> findByTenantId(String tenantId);
    
    List<InviteLink> findByCreatedBy(String createdBy);
}
