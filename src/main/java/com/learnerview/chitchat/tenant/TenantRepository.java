package com.learnerview.chitchat.tenant;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);

    List<Tenant> findByOwnerId(String ownerId);
}
