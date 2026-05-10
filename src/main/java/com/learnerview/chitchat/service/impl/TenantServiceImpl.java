package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.Tenant;
import com.learnerview.chitchat.repositories.TenantRepository;
import com.learnerview.chitchat.repositories.TenantMemberRepository;
import com.learnerview.chitchat.service.TenantService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;

    public TenantServiceImpl(TenantRepository tenantRepository, TenantMemberRepository tenantMemberRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
    }

    @Override
    public Tenant createTenant(String name, String slug, String ownerId, String description) {
        if (!isSlugAvailable(slug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace slug already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .ownerId(ownerId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        return tenantRepository.save(tenant);
    }

    @Override
    public Optional<Tenant> getTenantById(String id) {
        return tenantRepository.findById(id);
    }

    @Override
    public Optional<Tenant> getTenantBySlug(String slug) {
        return tenantRepository.findBySlug(slug);
    }

    @Override
    public List<Tenant> getTenantsByOwner(String ownerId) {
        return tenantRepository.findByOwnerId(ownerId);
    }

    @Override
    public Tenant updateTenant(String tenantId, String name, String description) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        if (description != null) {
            tenant.setDescription(description.trim());
        }

        return tenantRepository.save(tenant);
    }

    @Override
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        tenant.setActive(false);
        tenantRepository.save(tenant);
    }

    @Override
    public boolean isSlugAvailable(String slug) {
        return tenantRepository.findBySlug(slug).isEmpty();
    }

    @Override
    public int getMemberCount(String tenantId) {
        return (int) tenantMemberRepository.findByTenantId(tenantId).stream().count();
    }
}
