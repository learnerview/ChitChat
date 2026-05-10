package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.ChangePasswordRequest;
import com.learnerview.chitchat.dto.UserProfileResponse;
import com.learnerview.chitchat.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public List<UserProfileResponse> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query).stream().map(this::toResponse).toList();
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(org.springframework.security.core.Authentication auth) {
        return userService.findByUsername(auth.getName())
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("User not found: " + auth.getName()));
    }

    @GetMapping("/{username}")
    public UserProfileResponse getUser(@PathVariable String username) {
        return userService.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@RequestParam String displayName,
                                             org.springframework.security.core.Authentication auth) {
        return toResponse(userService.updateProfile(auth.getName(), displayName));
    }

    @DeleteMapping("/me")
    public void deleteAccount(org.springframework.security.core.Authentication auth) {
        userService.deleteAccount(auth.getName());
    }

    @PostMapping("/password")
    public void changePassword(@jakarta.validation.Valid @RequestBody ChangePasswordRequest request,
                               org.springframework.security.core.Authentication auth) {
        userService.changePassword(auth.getName(), request.getCurrentPassword(), request.getNewPassword());
    }

    private UserProfileResponse toResponse(com.learnerview.chitchat.entities.User user) {
        return new UserProfileResponse(
                user.getUsername(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
