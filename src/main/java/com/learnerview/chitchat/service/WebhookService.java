package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.WebhookSubscription;

import java.util.List;
import java.util.Set;

public interface WebhookService {
    WebhookSubscription register(String url, Set<String> events, String secret);

    List<WebhookSubscription> list();

    void delete(String id);
}
