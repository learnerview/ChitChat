package com.learnerview.chitchat.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryWorkerTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;

    @Mock
    private HttpClient httpClient;

    @Mock
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private WebhookDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new WebhookDeliveryWorker(
                deliveryRepository, subscriptionRepository, httpClient, mongoTemplate,
                MAX_ATTEMPTS, 50, 300);
    }

    @Test
    void deliveriesClaimedByAnotherWorkerAreSkipped() throws Exception {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimLost();

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.PENDING);
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(deliveryRepository, never()).save(any(WebhookDelivery.class));
    }

    @Test
    void successfulDeliveryIsMarkedDelivered() throws Exception {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimSuccess();
        stubSubscription(true);
        stubHttpStatus(200);

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void failedDeliveryIsRetriedWithBackoff() throws Exception {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimSuccess();
        stubSubscription(true);
        stubHttpStatus(500);

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
        assertThat(delivery.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(20));
    }

    @Test
    void exhaustedAttemptsAreDeadLettered() throws Exception {
        WebhookDelivery delivery = pending(MAX_ATTEMPTS - 1);
        stubDue(delivery);
        stubClaimSuccess();
        stubSubscription(true);
        stubHttpStatus(503);

        worker.deliverDue();

        assertThat(delivery.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.FAILED);
    }

    @Test
    void transportErrorsAreRetried() throws Exception {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimSuccess();
        stubSubscription(true);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastError()).isEqualTo("connection refused");
    }

    @Test
    void removedSubscriptionFailsPermanentlyWithoutHttp() {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimSuccess();
        when(subscriptionRepository.findById("sub-1")).thenReturn(Optional.empty());

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.FAILED);
        verifyNoHttpCall();
    }

    @Test
    void deactivatedSubscriptionFailsPermanentlyWithoutHttp() {
        WebhookDelivery delivery = pending(0);
        stubDue(delivery);
        stubClaimSuccess();
        stubSubscription(false);

        worker.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo(WebhookDelivery.DeliveryStatus.FAILED);
        verifyNoHttpCall();
    }

    private WebhookDelivery pending(int attempts) {
        return WebhookDelivery.builder()
                .id("del-1")
                .tenantId("tenant-1")
                .subscriptionId("sub-1")
                .event("message.sent")
                .payload("{\"event\":\"message.sent\"}")
                .status(WebhookDelivery.DeliveryStatus.PENDING)
                .attempts(attempts)
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void stubDue(WebhookDelivery delivery) {
        when(deliveryRepository.findByStatusAndNextAttemptAtLessThanEqual(
                eq(WebhookDelivery.DeliveryStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(delivery));
    }

    private void stubClaimSuccess() {
        when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(WebhookDelivery.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));
    }

    private void stubClaimLost() {
        when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class), eq(WebhookDelivery.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 0L, null));
    }

    private void stubSubscription(boolean active) {
        when(subscriptionRepository.findById("sub-1"))
                .thenReturn(Optional.of(WebhookSubscription.builder()
                        .id("sub-1").tenantId("tenant-1")
                        .url("https://93.184.216.34/hook")
                        .active(active).build()));
    }

    @SuppressWarnings("unchecked")
    private void stubHttpStatus(int status) throws Exception {
        HttpResponse<Void> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    private void verifyNoHttpCall() {
        try {
            verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
