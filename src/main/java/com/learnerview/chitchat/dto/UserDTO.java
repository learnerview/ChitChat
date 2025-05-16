package com.learnerview.chitchat.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    @NotBlank
    private String username;
    
    @NotBlank
    @Size(max = 100)
    private String displayName;
    
    private String avatarUrl;
    private boolean online;
    private String bio;
}