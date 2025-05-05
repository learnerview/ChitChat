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
@Document(collection = "conversation_memberships")
@CompoundIndex(def = "{'conversationId': 1, 'username': 1}")
public class ConversationMembership {
    @Id
    private String id;
    
    private String conversationId;
    private String username;
    
    @Builder.Default
    private MembershipRole role = MembershipRole.MEMBER;
    
    @Builder.Default
    private MembershipStatus status = MembershipStatus.APPROVED;
    
    private LocalDateTime joinedAt;
    private LocalDateTime lastClearedAt;
    
    @Builder.Default
    private boolean hidden = false;
    
    @Builder.Default
    private boolean muted = false;
    
    @Builder.Default
    private boolean pinned = false;
    
    public ConversationMembership(String conversationId, String username) {
        this.conversationId = conversationId;
        this.username = username;
        this.joinedAt = LocalDateTime.now();
        this.status = MembershipStatus.APPROVED;
        this.role = MembershipRole.MEMBER;
    }
}
