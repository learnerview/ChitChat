package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "tenant_members")
@CompoundIndexes({
    @CompoundIndex(def = "{'tenantId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(def = "{'tenantId': 1, 'role': 1}")
})
public class TenantMember {
    @Id
    private String id;

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Role is required")
    private String role; // OWNER, ADMIN, MEMBER

    private LocalDateTime joinedAt;

    public enum Role {
        OWNER, ADMIN, MEMBER
    }
}
