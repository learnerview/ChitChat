package com.learnerview.chitchat.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "tenant_members")
@CompoundIndexes({
    @CompoundIndex(name = "tenant_user_unique", def = "{'tenantId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "tenant_role", def = "{'tenantId': 1, 'role': 1}")
})
public class TenantMember {

    @Id
    private String id;

    private String tenantId;

    private String userId;

    private String role;

    private LocalDateTime joinedAt;

    /** Soft removal: membership history is kept for audit and reinstatement. */
    private LocalDateTime removedAt;

    public boolean isActive() {
        return removedAt == null;
    }

    public enum Role {
        OWNER, ADMIN, MEMBER
    }
}
