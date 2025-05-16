package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    
    User register(User user);
    
    Optional<User> findByUsername(String username);
    
    List<User> searchUsers(String query);
    
    User updateProfile(String username, String displayName, String bio, String avatarUrl);
    
    User blockUser(String username, String targetUsername);
    
    User unblockUser(String username, String targetUsername);
    
    void deleteAccount(String username);
    
    User updatePrivacy(String username, boolean ghostMode, boolean showLastSeen);
    
    User save(User user);
}
