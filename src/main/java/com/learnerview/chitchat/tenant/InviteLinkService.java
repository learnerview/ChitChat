package com.learnerview.chitchat.tenant;

import java.time.LocalDateTime;
import java.util.List;

public interface InviteLinkService {

    InviteLink createInviteLink(String tenantId, String createdBy, LocalDateTime expiresAt);

    /** Accepts an invite and returns the joined tenant's id. */
    String acceptInvite(String token, String userId);

    void revokeInvite(String token, String tenantId);

    List<InviteLink> getTenantInvites(String tenantId);
}
