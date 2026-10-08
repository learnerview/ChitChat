package com.learnerview.chitchat.webhook;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookSubscriptionRepository extends MongoRepository<WebhookSubscription, String> {

    List<WebhookSubscription> findByTenantId(String tenantId);

    List<WebhookSubscription> findByTenantIdAndActiveTrueAndEventsContaining(String tenantId, String event);

    Optional<WebhookSubscription> findByIdAndTenantId(String id, String tenantId);
}
