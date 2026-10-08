package com.learnerview.chitchat.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StompRealtimeEventPublisherTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final StompRealtimeEventPublisher publisher = new StompRealtimeEventPublisher(template);

    @Test
    void publishSendsToConversationTopic() {
        RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED,
                "t1", "c1", 5L, "payload");

        publisher.publish("c1", event);

        verify(template).convertAndSend("/topic/conversations/c1", event);
    }

    @Test
    void publishToUserSendsToThePrivateSyncQueue() {
        RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.SYNC_COMPLETE,
                "t1", "c1", null, "payload");

        publisher.publishToUser("u1", event);

        verify(template).convertAndSendToUser("u1", "/queue/sync", event);
    }

    @Test
    void deliveryFailuresNeverPropagate() {
        RealtimeEvent event = RealtimeEvent.of(RealtimeEventType.MESSAGE_CREATED,
                "t1", "c1", 5L, "payload");
        doThrow(new RuntimeException("broker down"))
                .when(template).convertAndSend("/topic/conversations/c1", event);
        doThrow(new RuntimeException("broker down"))
                .when(template).convertAndSendToUser("u1", "/queue/sync", event);

        assertThatCode(() -> {
            publisher.publish("c1", event);
            publisher.publishToUser("u1", event);
        }).doesNotThrowAnyException();
    }
}
