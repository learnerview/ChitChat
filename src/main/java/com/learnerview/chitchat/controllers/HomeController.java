package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.UserDTO;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HomeController {

    @Autowired
    private UserService userService;

    @GetMapping("/home")
    public UserDTO getHomeData(Authentication auth) {
        User user = userService.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return UserDTO.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .online(user.isOnline())
                .bio(user.getBio())
                .build();
    }
}
