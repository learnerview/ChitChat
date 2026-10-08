package com.learnerview.chitchat.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-instance broker publisher. Cross-instance fanout routes through Redis
 * pub/sub behind this same interface (Phase 4): each instance subscribes to a
 * shared channel and forwards to its local broker.
 */
@Component
public class StompRealtimeEventPublisher implements RealtimeEventPublisher {

    /** Private per-user destination clients subscribe to as {@code /user/queue/sync}. */
    public static final String USER_SYNC_QUEUE = "/queue/sync";

    private static final Logger log = LoggerFactory.getLogger(StompRealtimeEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(String conversationId, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, event);
        } catch (Exception ex) {
            // Realtime delivery is best effort; the durable state is already saved.
            log.warn("Failed to publish {} to conversation {}: {}",
                    event.type(), conversationId, ex.getMessage());
        }
    }

    @Override
    public void publishToUser(String userId, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(userId, USER_SYNC_QUEUE, event);
        } catch (Exception ex) {
            log.warn("Failed to publish {} to user {}: {}",
                    event.type(), userId, ex.getMessage());
        }
    }
}
