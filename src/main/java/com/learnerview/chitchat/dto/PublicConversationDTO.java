package com.learnerview.chitchat.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicConversationDTO {
    private String id;
    private String name;
    private String handle;
    private String description;
    private int memberCount;
    private boolean isMember;
}
