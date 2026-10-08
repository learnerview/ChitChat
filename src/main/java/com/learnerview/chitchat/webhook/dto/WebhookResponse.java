package com.learnerview.chitchat.webhook.dto;

import com.learnerview.chitchat.webhook.WebhookSubscription;

import java.time.LocalDateTime;
import java.util.Set;

/** Secret is intentionally never exposed. */
public record WebhookResponse(
        String id,
        String url,
        Set<String> events,
        boolean active,
        LocalDateTime createdAt
) {
    public static WebhookResponse from(WebhookSubscription subscription) {
        return new WebhookResponse(
                subscription.getId(),
                subscription.getUrl(),
                subscription.getEvents(),
                subscription.isActive(),
                subscription.getCreatedAt());
    }
}
