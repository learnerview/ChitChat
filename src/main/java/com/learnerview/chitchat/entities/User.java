package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
@CompoundIndexes({
    @CompoundIndex(def = "{'tenantId': 1, 'username': 1}", unique = true),
    @CompoundIndex(def = "{'tenantId': 1, 'displayName': 1}")
})
public class User {
    @Id
    private String id;

    private String tenantId;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 1, max = 80, message = "Display name must be between 1 and 80 characters")
    private String displayName;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    private String externalUserId;
    
    private LocalDateTime createdAt;
}
