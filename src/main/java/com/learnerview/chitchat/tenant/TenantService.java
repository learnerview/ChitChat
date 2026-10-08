package com.learnerview.chitchat.tenant;

import java.util.Optional;

public interface TenantService {

    Tenant createTenant(String name, String slug, String ownerId, String description);

    Optional<Tenant> getTenantById(String id);

    Tenant updateTenant(String tenantId, String name, String description);

    void deleteTenant(String tenantId);
}
