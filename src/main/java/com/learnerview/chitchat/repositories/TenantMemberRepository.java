package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.TenantMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantMemberRepository extends MongoRepository<TenantMember, String> {
    Optional<TenantMember> findByTenantIdAndUserId(String tenantId, String userId);
    
    List<TenantMember> findByTenantId(String tenantId);
    
    List<TenantMember> findByUserId(String userId);
    
    void deleteByTenantIdAndUserId(String tenantId, String userId);
}
