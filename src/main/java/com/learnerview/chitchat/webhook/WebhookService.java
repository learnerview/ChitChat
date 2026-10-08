package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.webhook.dto.WebhookResponse;

import java.util.List;
import java.util.Set;

public interface WebhookService {

    WebhookResponse register(String url, Set<String> events, String secret);

    WebhookResponse update(String id, String url, Set<String> events, Boolean active);

    List<WebhookResponse> list();

    void delete(String id);
}
