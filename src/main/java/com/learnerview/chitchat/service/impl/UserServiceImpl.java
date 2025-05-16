package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User register(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setOnline(false);
        
        return userRepository.save(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(query, query);
    }

    @Override
    public User updateProfile(String username, String displayName, String bio, String avatarUrl) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (displayName != null) user.setDisplayName(displayName);
        if (bio != null) user.setBio(bio);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
        
        return userRepository.save(user);
    }

    @Override
    public User blockUser(String username, String targetUsername) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        User target = findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));
        
        if (!user.hasBlocked(target.getId())) {
            user.getBlockedUserIds().add(target.getId());
        }
        
        return userRepository.save(user);
    }

    @Override
    public User unblockUser(String username, String targetUsername) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        User target = findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));
        
        user.getBlockedUserIds().remove(target.getId());
        
        return userRepository.save(user);
    }

    @Override
    public void deleteAccount(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        userRepository.delete(user);
    }

    @Override
    public User updatePrivacy(String username, boolean ghostMode, boolean showLastSeen) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setGhostMode(ghostMode);
        user.setShowLastSeen(showLastSeen);
        
        return userRepository.save(user);
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }
}
