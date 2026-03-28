package com.learnerview.chitchat.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT.get();
    }

    public static String getRequiredTenantId() {
        String tenantId = TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Tenant-Id header");
        }
        return tenantId;
    }

    public static void clear() {
        TENANT.remove();
    }
}
