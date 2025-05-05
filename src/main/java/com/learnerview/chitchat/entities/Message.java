package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "messages")
public class Message {
    @Id
    private String id;
    private String conversationId;
    private String sender;
    
    @TextIndexed
    private String content;
    
    @Field("timeStamp")
    private LocalDateTime timeStamp;
    
    // Read Receipts
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    
    @Builder.Default
    private List<String> readBy = new ArrayList<>();
    
    // Threading (reply to message)
    private String replyToId;
    
    // Auto-parsed mentions (@username)
    @Builder.Default
    private List<String> mentions = new ArrayList<>();
    
    // File Attachments
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();
    
    // Message Editing
    private String originalContent;
    private LocalDateTime editedAt;
    @Builder.Default
    private boolean edited = false;

    // Message Reactions (WhatsApp-like)
    @Builder.Default
    private List<Reaction> reactions = new ArrayList<>();

    @Builder.Default
    private boolean forwarded = false;
    
    // List of users who have deleted this message for themselves
    @Builder.Default
    private List<String> deletedBy = new ArrayList<>();

    public Message(String conversationId, String sender, String content) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
        this.timeStamp = LocalDateTime.now();
        this.status = MessageStatus.SENT;
        this.readBy = new ArrayList<>();
        this.mentions = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.reactions = new ArrayList<>();
        this.deletedBy = new ArrayList<>();
    }
}
