package com.learnerview.chitchat.common.event;

import java.util.Map;

/**
 * Outbound integration events (webhooks). In-process realtime fanout is handled
 * separately by the realtime module.
 */
public interface EventPublisherService {
    void publish(String tenantId, String event, Map<String, Object> data);
}
