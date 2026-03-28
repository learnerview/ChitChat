package com.learnerview.chitchat.service;

import java.util.Map;

public interface EventPublisherService {
    void publish(String tenantId, String event, Map<String, Object> data);
}
