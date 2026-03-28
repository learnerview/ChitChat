package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.AuthResponse;
import com.learnerview.chitchat.dto.LoginRequest;
import com.learnerview.chitchat.dto.RegisterRequest;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.security.JwtTokenProvider;
import com.learnerview.chitchat.service.UserService;
import com.learnerview.chitchat.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserService userService,
                        AuthenticationManager authenticationManager,
                        JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String jwt = tokenProvider.generateToken(authentication, TenantContext.getRequiredTenantId());
        
        User user = userService.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        AuthResponse authResponse = new AuthResponse(jwt, user.getUsername(), user.getDisplayName());
        
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userService.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setDisplayName(registerRequest.getDisplayName());

        userService.register(user);

        return ResponseEntity.ok("Registration successful. Please login.");
    }

    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchangeExternalToken(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String jwt = tokenProvider.generateTokenForUsername(user.getUsername(), TenantContext.getRequiredTenantId());

        return ResponseEntity.ok(new AuthResponse(jwt, user.getUsername(), user.getDisplayName()));
    }
}
