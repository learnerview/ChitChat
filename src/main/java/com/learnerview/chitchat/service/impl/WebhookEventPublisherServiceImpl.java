package com.learnerview.chitchat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.chitchat.entities.WebhookSubscription;
import com.learnerview.chitchat.repositories.WebhookSubscriptionRepository;
import com.learnerview.chitchat.service.EventPublisherService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WebhookEventPublisherServiceImpl implements EventPublisherService {
    private static final Logger log = LoggerFactory.getLogger(WebhookEventPublisherServiceImpl.class);

    private final WebhookSubscriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookEventPublisherServiceImpl(WebhookSubscriptionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public void publish(String tenantId, String event, Map<String, Object> data) {
        List<WebhookSubscription> subscriptions = repository.findByTenantIdAndActiveTrueAndEventsContaining(tenantId, event);
        if (subscriptions.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "tenantId", tenantId,
                "event", event,
                "timestamp", LocalDateTime.now().toString(),
                "data", data
        );

        for (WebhookSubscription subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    private void send(WebhookSubscription subscription, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                requestBuilder.header("X-Signature", signPayload(body, subscription.getSecret()));
            }

            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((resp, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send webhook to {}: {}", subscription.getUrl(), ex.getMessage());
                    } else if (resp.statusCode() >= 400) {
                        log.warn("Webhook to {} returned status {}", subscription.getUrl(), resp.statusCode());
                    }
                });
        } catch (Exception ex) {
            log.error("Error preparing webhook for {}: {}", subscription.getUrl(), ex.getMessage());
        }
    }

    private String signPayload(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate webhook signature", ex);
        }
    }
}
