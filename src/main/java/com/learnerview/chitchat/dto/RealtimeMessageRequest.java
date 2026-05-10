package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeMessageRequest {
    @NotBlank(message = "Message content is required")
    private String content;
    private String replyToId;
}
