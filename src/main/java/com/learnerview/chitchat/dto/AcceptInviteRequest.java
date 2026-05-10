package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInviteRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
