package com.learnerview.chitchat.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "webhook_subscriptions")
public class WebhookSubscription {
    @Id
    private String id;

    private String tenantId;
    private String url;

    @Builder.Default
    private Set<String> events = new HashSet<>();

    @Builder.Default
    private boolean active = true;

    private String secret;
    private LocalDateTime createdAt;
}
