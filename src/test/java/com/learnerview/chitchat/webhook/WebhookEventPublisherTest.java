package com.learnerview.chitchat.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookEventPublisherTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    private WebhookEventPublisher publisher;

    @Test
    void publishEnqueuesOnePendingDeliveryPerSubscription() {
        publisher = new WebhookEventPublisher(
                subscriptionRepository, deliveryRepository, new ObjectMapper());
        when(subscriptionRepository.findByTenantIdAndActiveTrueAndEventsContaining(TENANT, "message.sent"))
                .thenReturn(List.of(subscription("sub-1"), subscription("sub-2")));

        publisher.publish(TENANT, "message.sent", Map.of("messageId", "m-1"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, times(2)).save(captor.capture());

        List<WebhookDelivery> deliveries = captor.getAllValues();
        assertThat(deliveries).extracting(WebhookDelivery::getStatus)
                .containsOnly(WebhookDelivery.DeliveryStatus.PENDING);
        assertThat(deliveries).extracting(WebhookDelivery::getSubscriptionId)
                .containsExactlyInAnyOrder("sub-1", "sub-2");
        assertThat(deliveries.get(0).getPayload()).contains("\"event\":\"message.sent\"");
        assertThat(deliveries.get(0).getPayload()).contains("\"messageId\":\"m-1\"");
        assertThat(deliveries.get(0).getNextAttemptAt()).isNotNull();
    }

    @Test
    void publishNeverFailsTheOriginatingRequest() {
        publisher = new WebhookEventPublisher(
                subscriptionRepository, deliveryRepository, new ObjectMapper());
        when(subscriptionRepository.findByTenantIdAndActiveTrueAndEventsContaining(anyString(), anyString()))
                .thenThrow(new RuntimeException("mongo down"));

        assertThatCode(() -> publisher.publish(TENANT, "message.sent", Map.of("messageId", "m-1")))
                .doesNotThrowAnyException();
    }

    private WebhookSubscription subscription(String id) {
        return WebhookSubscription.builder()
                .id(id).tenantId(TENANT).url("https://93.184.216.34/hook")
                .active(true).build();
    }
}
