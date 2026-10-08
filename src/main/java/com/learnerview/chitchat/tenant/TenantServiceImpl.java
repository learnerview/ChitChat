package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.webhook.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TenantServiceImpl implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final InviteLinkRepository inviteLinkRepository;

    public TenantServiceImpl(TenantRepository tenantRepository,
                             TenantMemberRepository tenantMemberRepository,
                             WebhookSubscriptionRepository webhookSubscriptionRepository,
                             InviteLinkRepository inviteLinkRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.webhookSubscriptionRepository = webhookSubscriptionRepository;
        this.inviteLinkRepository = inviteLinkRepository;
    }

    @Override
    public Tenant createTenant(String name, String slug, String ownerId, String description) {
        if (!tenantRepository.findBySlug(slug).isEmpty()) {
            throw new ApiException(ErrorCode.SLUG_TAKEN);
        }

        // The workspace and its OWNER membership are created together; if the
        // membership write fails the workspace is removed again so no orphaned,
        // unmanageable workspace (with a burned slug) can survive.
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .ownerId(ownerId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        Tenant saved = tenantRepository.save(tenant);
        try {
            tenantMemberRepository.save(TenantMember.builder()
                    .tenantId(saved.getId())
                    .userId(ownerId)
                    .role(TenantMember.Role.OWNER.name())
                    .joinedAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException ex) {
            try {
                tenantRepository.deleteById(saved.getId());
            } catch (Exception cleanupFailure) {
                log.warn("Failed to clean up workspace {} after member creation failure: {}",
                        saved.getId(), cleanupFailure.getMessage());
            }
            throw ex;
        }
        return saved;
    }

    @Override
    public Optional<Tenant> getTenantById(String id) {
        return tenantRepository.findById(id);
    }

    @Override
    public Tenant updateTenant(String tenantId, String name, String description) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.WORKSPACE_NOT_FOUND));

        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }
        if (description != null) {
            tenant.setDescription(description.trim());
        }
        tenant.setUpdatedAt(LocalDateTime.now());

        return tenantRepository.save(tenant);
    }

    @Override
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.WORKSPACE_NOT_FOUND));

        tenant.setActive(false);
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantRepository.save(tenant);

        // A deleted workspace stops all outbound integrations and pending invites.
        webhookSubscriptionRepository.findByTenantId(tenantId).forEach(subscription -> {
            subscription.setActive(false);
            webhookSubscriptionRepository.save(subscription);
        });
        inviteLinkRepository.findByTenantId(tenantId).forEach(inviteLinkRepository::delete);
    }
}
