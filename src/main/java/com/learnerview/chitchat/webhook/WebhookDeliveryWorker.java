package com.learnerview.chitchat.webhook;

import com.mongodb.client.result.UpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Delivers queued {@link WebhookDelivery} records with exponential backoff
 * (30s * 2^attempts, capped at 30 minutes). After {@code app.webhook.max-attempts}
 * failures the delivery is dead-lettered as {@code FAILED}.
 */
@Component
public class WebhookDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);
    private static final long BASE_BACKOFF_MS = 30_000L;
    private static final long MAX_BACKOFF_MS = 1_800_000L;
    private static final int MAX_ERROR_LENGTH = 500;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final HttpClient httpClient;
    private final MongoTemplate mongoTemplate;
    private final int maxAttempts;
    private final int batchSize;
    private final long leaseSeconds;

    public WebhookDeliveryWorker(WebhookDeliveryRepository deliveryRepository,
                                 WebhookSubscriptionRepository subscriptionRepository,
                                 HttpClient httpClient,
                                 MongoTemplate mongoTemplate,
                                 @Value("${app.webhook.max-attempts:8}") int maxAttempts,
                                 @Value("${app.webhook.batch-size:50}") int batchSize,
                                 @Value("${app.webhook.lease-seconds:300}") long leaseSeconds) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.httpClient = httpClient;
        this.mongoTemplate = mongoTemplate;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${app.webhook.poll-interval-ms:5000}",
            initialDelayString = "${app.webhook.poll-initial-delay-ms:10000}")
    public void deliverDue() {
        LocalDateTime now = LocalDateTime.now();
        List<WebhookDelivery> due = new java.util.ArrayList<>(
                deliveryRepository.findByStatusAndNextAttemptAtLessThanEqual(
                        WebhookDelivery.DeliveryStatus.PENDING, now, PageRequest.of(0, batchSize)));
        // Recover deliveries whose previous worker crashed mid-attempt.
        due.addAll(deliveryRepository.findByStatusAndLeaseUntilLessThanEqual(
                WebhookDelivery.DeliveryStatus.PROCESSING, now, PageRequest.of(0, batchSize)));
        for (WebhookDelivery delivery : due) {
            try {
                if (tryClaim(delivery)) {
                    attempt(delivery);
                }
            } catch (Exception ex) {
                log.error("Unexpected error delivering webhook {}: {}", delivery.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Atomic PENDING/lease-expired -> PROCESSING transition. With multiple
     * application instances only one worker can win the claim.
     */
    private boolean tryClaim(WebhookDelivery delivery) {
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(delivery.getId())
                        .and("status").is(delivery.getStatus().name())),
                new Update()
                        .set("status", WebhookDelivery.DeliveryStatus.PROCESSING.name())
                        .set("leaseUntil", LocalDateTime.now().plusSeconds(leaseSeconds)),
                WebhookDelivery.class);
        boolean claimed = result.getModifiedCount() > 0;
        if (claimed) {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.PROCESSING);
        }
        return claimed;
    }

    private void attempt(WebhookDelivery delivery) {
        WebhookSubscription subscription = subscriptionRepository
                .findById(delivery.getSubscriptionId())
                .orElse(null);
        if (subscription == null || !subscription.isActive()) {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            delivery.setLeaseUntil(null);
            delivery.setLastError("Subscription removed or deactivated");
            deliveryRepository.save(delivery);
            return;
        }

        try {
            // DNS is re-validated at delivery time (rebinding between register and send).
            URI uri = WebhookUrlValidator.validate(subscription.getUrl());

            String timestamp = Instant.now().toString();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("X-ChitChat-Event", delivery.getEvent())
                    .header("X-ChitChat-Delivery-Id", delivery.getId())
                    .header("X-ChitChat-Timestamp", timestamp)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()));

            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                requestBuilder.header("X-ChitChat-Signature",
                        "sha256=" + sign(timestamp + "." + delivery.getPayload(), subscription.getSecret()));
            }

            HttpResponse<Void> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.DELIVERED);
                delivery.setLeaseUntil(null);
                delivery.setDeliveredAt(LocalDateTime.now());
            } else {
                scheduleRetry(delivery, "HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            scheduleRetry(delivery, ex.getMessage());
        }
        deliveryRepository.save(delivery);
    }

    private void scheduleRetry(WebhookDelivery delivery, String error) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLeaseUntil(null);
        delivery.setLastError(error == null ? "unknown error"
                : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH)));
        if (delivery.getAttempts() >= maxAttempts) {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            log.warn("Webhook delivery {} dead-lettered after {} attempts: {}",
                    delivery.getId(), delivery.getAttempts(), delivery.getLastError());
            return;
        }
        delivery.setStatus(WebhookDelivery.DeliveryStatus.PENDING);
        long backoff = Math.min(BASE_BACKOFF_MS * (1L << (delivery.getAttempts() - 1)), MAX_BACKOFF_MS);
        delivery.setNextAttemptAt(LocalDateTime.now().plusNanos(backoff * 1_000_000L));
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate webhook signature", ex);
        }
    }
}
