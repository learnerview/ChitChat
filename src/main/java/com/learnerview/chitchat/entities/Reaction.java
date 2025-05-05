package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "reactions")
public class Reaction {
    @Id
    private String id;
    
    private String messageId;
    private String userId;
    private String emoji; // 👍, ❤️, 😂, etc.
    
    public Reaction(String messageId, String userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }
}
