package com.learnerview.chitchat.tenant;

import java.util.List;
import java.util.Optional;

public interface MembershipService {

    TenantMember addMember(String tenantId, String userId, String role);

    void removeMember(String tenantId, String userId);

    Optional<TenantMember> getMembership(String tenantId, String userId);

    List<TenantMember> getTenantMembers(String tenantId);

    List<TenantMember> getUserMemberships(String userId);

    boolean isMember(String tenantId, String userId);

    boolean hasRole(String tenantId, String userId, String role);
}
