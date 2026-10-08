package com.learnerview.chitchat.realtime;

import java.time.LocalDateTime;

/**
 * Typed envelope for everything pushed over WebSocket. Clients switch on
 * {@code type} instead of guessing at payload shapes.
 */
public record RealtimeEvent(
        RealtimeEventType type,
        String tenantId,
        String conversationId,
        Long sequence,
        Object payload,
        LocalDateTime timestamp
) {
    public static RealtimeEvent of(RealtimeEventType type, String tenantId, String conversationId,
                                   Long sequence, Object payload) {
        return new RealtimeEvent(type, tenantId, conversationId, sequence, payload, LocalDateTime.now());
    }
}
