package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Tenant;

import java.util.List;
import java.util.Optional;

public interface TenantService {
    // Create a new workspace
    Tenant createTenant(String name, String slug, String ownerId, String description);
    
    // Get tenant by ID
    Optional<Tenant> getTenantById(String id);
    
    // Get tenant by slug
    Optional<Tenant> getTenantBySlug(String slug);
    
    // List tenants owned by user
    List<Tenant> getTenantsByOwner(String ownerId);
    
    // Update tenant details
    Tenant updateTenant(String tenantId, String name, String description);
    
    // Delete tenant
    void deleteTenant(String tenantId);
    
    // Check if slug is available
    boolean isSlugAvailable(String slug);
    
    // Get all members of a tenant
    int getMemberCount(String tenantId);
}
