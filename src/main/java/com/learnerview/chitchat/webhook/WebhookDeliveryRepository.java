package com.learnerview.chitchat.webhook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends MongoRepository<WebhookDelivery, String> {

    List<WebhookDelivery> findByStatusAndNextAttemptAtLessThanEqual(
            WebhookDelivery.DeliveryStatus status, LocalDateTime now, Pageable pageable);

    /** Deliveries whose worker lease expired (crashed worker recovery). */
    List<WebhookDelivery> findByStatusAndLeaseUntilLessThanEqual(
            WebhookDelivery.DeliveryStatus status, LocalDateTime now, Pageable pageable);
}
