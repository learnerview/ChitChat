package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInviteRequest {
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;
    
    private Integer expiresInDays;
}
