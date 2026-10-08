package com.learnerview.chitchat.tenant;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantMemberRepository extends MongoRepository<TenantMember, String> {

    Optional<TenantMember> findByTenantIdAndUserId(String tenantId, String userId);

    List<TenantMember> findByTenantIdAndRemovedAtIsNull(String tenantId);

    List<TenantMember> findByUserIdAndRemovedAtIsNull(String userId);

    long countByTenantIdAndRoleAndRemovedAtIsNull(String tenantId, String role);

    List<TenantMember> findByTenantIdAndUserIdIn(String tenantId, Collection<String> userIds);
}
