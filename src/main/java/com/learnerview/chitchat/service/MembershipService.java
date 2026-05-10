package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.TenantMember;

import java.util.List;
import java.util.Optional;

public interface MembershipService {
    // Add user to tenant (by owner/admin)
    TenantMember addMember(String tenantId, String userId, String role);
    
    // Remove user from tenant
    void removeMember(String tenantId, String userId);
    
    // Get user's membership in a tenant
    Optional<TenantMember> getMembership(String tenantId, String userId);
    
    // Get all members of a tenant
    List<TenantMember> getTenantMembers(String tenantId);
    
    // Get all tenants a user belongs to
    List<TenantMember> getUserMemberships(String userId);
    
    // Update user's role in tenant
    TenantMember updateMemberRole(String tenantId, String userId, String newRole);
    
    // Check if user is member of tenant
    boolean isMember(String tenantId, String userId);
    
    // Check if user has role in tenant
    boolean hasRole(String tenantId, String userId, String role);
}
