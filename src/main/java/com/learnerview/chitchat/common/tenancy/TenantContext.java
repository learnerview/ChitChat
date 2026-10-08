package com.learnerview.chitchat.common.tenancy;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;

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
            throw new ApiException(ErrorCode.MISSING_TENANT);
        }
        return tenantId;
    }

    public static void clear() {
        TENANT.remove();
    }
}
