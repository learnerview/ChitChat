package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.InviteLink;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InviteLinkService {
    // Create invite link for a tenant
    InviteLink createInviteLink(String tenantId, String createdBy, LocalDateTime expiresAt);
    
    // Get invite link by token
    Optional<InviteLink> getByToken(String token);
    
    // Accept/redeem invite link (join tenant)
    void acceptInvite(String token, String userId);
    
    // Revoke invite link
    void revokeInvite(String token);
    
    // Check if invite is valid
    boolean isValidInvite(String token);
    
    // Get all active invites for a tenant
    java.util.List<InviteLink> getTenantInvites(String tenantId);
}
