package com.learnerview.chitchat.realtime;

/**
 * Best-effort realtime delivery. Durable state is already persisted in MongoDB
 * before this is invoked; failures must never fail the originating request.
 *
 * <p>This interface is the transport seam. The current implementation talks to
 * the in-process STOMP broker; a horizontal scale-out swaps in a Redis pub/sub
 * backed implementation that forwards to every instance's local broker without
 * touching any calling service.
 */
public interface RealtimeEventPublisher {

    /** Fan an event out to every subscriber of the conversation topic. */
    void publish(String conversationId, RealtimeEvent event);

    /**
     * Deliver an event to a single user's private queue
     * ({@code /user/queue/sync}). Used by the resume protocol so catch-up
     * batches reach only the reconnecting client.
     */
    void publishToUser(String userId, RealtimeEvent event);
}
