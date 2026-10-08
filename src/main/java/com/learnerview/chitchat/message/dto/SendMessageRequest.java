package com.learnerview.chitchat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    private String content;

    @Size(max = 64, message = "replyToId must be at most 64 characters")
    private String replyToId;

    /** Client-generated idempotency key; retries with the same key return the same message. */
    @Size(max = 64, message = "clientMessageId must be at most 64 characters")
    private String clientMessageId;
}
