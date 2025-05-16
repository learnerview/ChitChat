package com.learnerview.chitchat.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {
    private String messageId;
    private String conversationId;
    private String conversationName;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
    private String highlightedContent; // For frontend highlighting
}
