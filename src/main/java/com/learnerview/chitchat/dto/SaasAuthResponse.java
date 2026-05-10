package com.learnerview.chitchat.dto;

import java.util.List;

public record SaasAuthResponse(
        String token,
        String type,
        String username,
        String displayName,
        String currentTenantId,
        String currentTenantName,
        List<TenantInfo> tenants
) {
    public SaasAuthResponse(String token, String username, String displayName,
                            String currentTenantId, String currentTenantName, List<TenantInfo> tenants) {
        this(token, "Bearer", username, displayName, currentTenantId, currentTenantName, tenants);
    }
}
