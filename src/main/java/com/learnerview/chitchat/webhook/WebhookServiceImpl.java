package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.webhook.dto.WebhookResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class WebhookServiceImpl implements WebhookService {

    private final WebhookSubscriptionRepository repository;
    private final AuthorizationService authorizationService;

    public WebhookServiceImpl(WebhookSubscriptionRepository repository,
                              AuthorizationService authorizationService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
    }

    @Override
    public WebhookResponse register(String url, Set<String> events, String secret) {
        String tenantId = TenantContext.getRequiredTenantId();
        authorizationService.requireTenantRole(tenantId,
                com.learnerview.chitchat.tenant.TenantMember.Role.OWNER,
                com.learnerview.chitchat.tenant.TenantMember.Role.ADMIN);

        WebhookUrlValidator.validate(url);
        if (events == null || events.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one event is required");
        }

        WebhookSubscription subscription = WebhookSubscription.builder()
                .tenantId(tenantId)
                .url(url.trim())
                .events(events)
                .secret(secret)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return WebhookResponse.from(repository.save(subscription));
    }

    @Override
    public WebhookResponse update(String id, String url, Set<String> events, Boolean active) {
        String tenantId = TenantContext.getRequiredTenantId();
        authorizationService.requireTenantRole(tenantId,
                com.learnerview.chitchat.tenant.TenantMember.Role.OWNER,
                com.learnerview.chitchat.tenant.TenantMember.Role.ADMIN);

        if (url == null && events == null && active == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "At least one field must be provided");
        }

        WebhookSubscription subscription = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Webhook subscription not found or does not belong to tenant"));

        if (url != null) {
            WebhookUrlValidator.validate(url);
            subscription.setUrl(url.trim());
        }
        if (events != null) {
            if (events.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "At least one event is required");
            }
            subscription.setEvents(events);
        }
        if (active != null) {
            subscription.setActive(active);
        }
        return WebhookResponse.from(repository.save(subscription));
    }

    @Override
    public List<WebhookResponse> list() {
        String tenantId = TenantContext.getRequiredTenantId();
        authorizationService.requireTenantMember(tenantId);
        return repository.findByTenantId(tenantId).stream()
                .map(WebhookResponse::from)
                .toList();
    }

    @Override
    public void delete(String id) {
        String tenantId = TenantContext.getRequiredTenantId();
        authorizationService.requireTenantRole(tenantId,
                com.learnerview.chitchat.tenant.TenantMember.Role.OWNER,
                com.learnerview.chitchat.tenant.TenantMember.Role.ADMIN);

        WebhookSubscription subscription = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Webhook subscription not found or does not belong to tenant"));
        repository.delete(subscription);
    }
}
