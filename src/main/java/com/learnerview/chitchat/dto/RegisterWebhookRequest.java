package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterWebhookRequest {
    @NotBlank(message = "Webhook URL is required")
    private String url;
    private Set<String> events;
    private String secret;
}
