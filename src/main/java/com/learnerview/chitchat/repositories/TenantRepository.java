package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {
    Optional<Tenant> findBySlug(String slug);
    
    java.util.List<Tenant> findByOwnerId(String ownerId);
}
