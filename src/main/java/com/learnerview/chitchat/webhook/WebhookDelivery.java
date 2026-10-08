package com.learnerview.chitchat.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Durable outbox record: every webhook event is persisted before it is
 * delivered, then retried with exponential backoff until it succeeds or is
 * dead-lettered.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "webhook_deliveries")
@CompoundIndexes({
    @CompoundIndex(name = "status_next_attempt", def = "{'status': 1, 'nextAttemptAt': 1}"),
    @CompoundIndex(name = "tenant_created", def = "{'tenantId': 1, 'createdAt': -1}")
})
public class WebhookDelivery {

    @Id
    private String id;

    private String tenantId;

    private String subscriptionId;

    private String event;

    /** Serialized JSON body delivered verbatim to the subscriber. */
    private String payload;

    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Builder.Default
    private int attempts = 0;

    private LocalDateTime nextAttemptAt;

    /** Set while a worker holds the delivery; a crashed worker's claim expires. */
    private LocalDateTime leaseUntil;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime deliveredAt;

    public enum DeliveryStatus {
        PENDING, PROCESSING, DELIVERED, FAILED
    }
}
