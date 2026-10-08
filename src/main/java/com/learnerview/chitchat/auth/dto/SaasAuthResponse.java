package com.learnerview.chitchat.auth.dto;

import java.util.List;

public record SaasAuthResponse(
        String token,
        String type,
        String userId,
        String username,
        String displayName,
        String currentTenantId,
        String currentTenantName,
        List<TenantInfo> tenants
) {
    public SaasAuthResponse(String token, String userId, String username, String displayName,
                            String currentTenantId, String currentTenantName, List<TenantInfo> tenants) {
        this(token, "Bearer", userId, username, displayName, currentTenantId, currentTenantName, tenants);
    }

    public record TenantInfo(
            String id,
            String name,
            String slug,
            String role
    ) {
    }
}
