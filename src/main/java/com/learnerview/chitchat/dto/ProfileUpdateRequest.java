package com.learnerview.chitchat.dto;

import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {
    
    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;
    
    @Size(max = 200, message = "Bio must be at most 200 characters")
    private String bio;
    
    private String avatarUrl;
    
    public ProfileUpdateRequest() {}
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
