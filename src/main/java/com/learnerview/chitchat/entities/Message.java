package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "messages")
@CompoundIndex(def = "{'tenantId': 1, 'conversationId': 1, 'createdAt': -1}")
public class Message {
    @Id
    private String id;

    private String tenantId;

    private String conversationId;
    private String senderId; // Changed from 'sender' - now stores userId, not username

    private String content;

    private String replyToId;

    private LocalDateTime createdAt;

    @Builder.Default
    private boolean edited = false;

    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime updatedAt;

    @Builder.Default
    private java.util.Set<String> readBy = new java.util.HashSet<>();
}
