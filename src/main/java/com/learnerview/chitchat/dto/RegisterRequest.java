package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 40, message = "Username must be between 3 and 40 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must be alphanumeric or underscore")
    private String username;

    @NotBlank(message = "Display name is required")
    @Size(min = 1, max = 80, message = "Display name must be between 1 and 80 characters")
    private String displayName;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
    
    public RegisterRequest() {}
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
}
