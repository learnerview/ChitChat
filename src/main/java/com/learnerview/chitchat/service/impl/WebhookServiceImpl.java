package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.WebhookSubscription;
import com.learnerview.chitchat.repositories.WebhookSubscriptionRepository;
import com.learnerview.chitchat.service.WebhookService;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class WebhookServiceImpl implements WebhookService {

    private final WebhookSubscriptionRepository repository;

    public WebhookServiceImpl(WebhookSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public WebhookSubscription register(String url, Set<String> events, String secret) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL is required");
        }
        if (events == null || events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one event is required");
        }

        WebhookSubscription sub = WebhookSubscription.builder()
                .tenantId(TenantContext.getRequiredTenantId())
                .url(url.trim())
                .events(events)
                .secret(secret)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(sub);
    }

    @Override
    public List<WebhookSubscription> list() {
        return repository.findByTenantId(TenantContext.getRequiredTenantId());
    }

    @Override
    public void delete(String id) {
        WebhookSubscription sub = repository.findByIdAndTenantId(id, TenantContext.getRequiredTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook subscription not found or does not belong to tenant"));
        repository.delete(sub);
    }
}
