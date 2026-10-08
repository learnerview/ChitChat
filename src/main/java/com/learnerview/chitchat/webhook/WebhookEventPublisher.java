package com.learnerview.chitchat.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.chitchat.common.event.EventPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox writer: persists one durable {@link WebhookDelivery} per matching
 * subscription. Performs NO HTTP itself - {@link WebhookDeliveryWorker}
 * delivers asynchronously with retries. Failures here must never fail the
 * originating request, so everything is best-effort.
 */
@Service
public class WebhookEventPublisher implements EventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventPublisher.class);

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public WebhookEventPublisher(WebhookSubscriptionRepository subscriptionRepository,
                                            WebhookDeliveryRepository deliveryRepository,
                                            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String tenantId, String event, Map<String, Object> data) {
        try {
            List<WebhookSubscription> subscriptions =
                    subscriptionRepository.findByTenantIdAndActiveTrueAndEventsContaining(tenantId, event);
            if (subscriptions.isEmpty()) {
                return;
            }

            Map<String, Object> payload = Map.of(
                    "tenantId", tenantId,
                    "event", event,
                    "timestamp", Instant.now().toString(),
                    "data", Map.copyOf(data)
            );
            String body = objectMapper.writeValueAsString(payload);

            LocalDateTime now = LocalDateTime.now();
            for (WebhookSubscription subscription : subscriptions) {
                deliveryRepository.save(WebhookDelivery.builder()
                        .tenantId(tenantId)
                        .subscriptionId(subscription.getId())
                        .event(event)
                        .payload(body)
                        .status(WebhookDelivery.DeliveryStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(now)
                        .createdAt(now)
                        .build());
            }
        } catch (Exception ex) {
            log.error("Failed to enqueue webhook deliveries for {} in tenant {}: {}",
                    event, tenantId, ex.getMessage());
        }
    }
}
