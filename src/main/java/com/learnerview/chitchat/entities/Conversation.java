package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(def = "{'tenantId': 1, 'participantIds': 1}"),
    @CompoundIndex(def = "{'tenantId': 1, 'createdAt': -1}"),
    @CompoundIndex(def = "{'tenantId': 1, 'type': 1, 'participantIds': 1}", unique = true, useGeneratedName = true, 
                   name = "unique_dm_index")
})
public class Conversation {
    @Id
    private String id;

    private String tenantId;
    
    private ConversationType type;
    
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    private String createdBy; // Now stores userId, not username
    
    private LocalDateTime createdAt;

    @Builder.Default
    private Set<String> participantIds = new HashSet<>(); // Changed from 'participants' - now stores userIds, not usernames
}
