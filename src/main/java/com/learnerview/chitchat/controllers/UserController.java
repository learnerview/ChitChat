package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.dto.ProfileUpdateRequest;
import com.learnerview.chitchat.service.UserService;
import com.learnerview.chitchat.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query);
    }

    @GetMapping("/profile")
    public User getProfile(org.springframework.security.core.Authentication auth) {
        return userService.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + auth.getName()));
    }

    @GetMapping("/{username}")
    public User getUser(@PathVariable String username) {
        return userService.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + username));
    }

    @PostMapping("/profile")
    public User updateProfile(@Valid @RequestBody ProfileUpdateRequest profile, org.springframework.security.core.Authentication auth) {
        return userService.updateProfile(auth.getName(), profile.getDisplayName(), profile.getBio(), profile.getAvatarUrl());
    }

    @PostMapping("/{targetUsername}/block")
    public User block(@PathVariable String targetUsername, org.springframework.security.core.Authentication auth) {
        return userService.blockUser(auth.getName(), targetUsername);
    }

    @PostMapping("/{targetUsername}/unblock")
    public User unblock(@PathVariable String targetUsername, org.springframework.security.core.Authentication auth) {
        return userService.unblockUser(auth.getName(), targetUsername);
    }

    @DeleteMapping("/me")
    public void deleteAccount(org.springframework.security.core.Authentication auth) {
        userService.deleteAccount(auth.getName());
    }

    @PutMapping("/privacy")
    public User updatePrivacy(@RequestParam boolean ghostMode, @RequestParam boolean showLastSeen, org.springframework.security.core.Authentication auth) {
        return userService.updatePrivacy(auth.getName(), ghostMode, showLastSeen);
    }
}
