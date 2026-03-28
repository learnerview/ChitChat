package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.WebhookSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSubscriptionRepository extends MongoRepository<WebhookSubscription, String> {
    List<WebhookSubscription> findByTenantId(String tenantId);

    List<WebhookSubscription> findByTenantIdAndActiveTrueAndEventsContaining(String tenantId, String event);
}
