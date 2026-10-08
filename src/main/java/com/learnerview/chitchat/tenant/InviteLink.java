package com.learnerview.chitchat.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "invite_links")
@CompoundIndex(name = "tenant_created_at", def = "{'tenantId': 1, 'createdAt': -1}")
public class InviteLink {

    @Id
    private String id;

    private String tenantId;

    private String createdBy;

    @Indexed(unique = true)
    private String token;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
