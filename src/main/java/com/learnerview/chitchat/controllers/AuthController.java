package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.AuthResponse;
import com.learnerview.chitchat.dto.LoginRequest;
import com.learnerview.chitchat.dto.RegisterRequest;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.security.JwtTokenProvider;
import com.learnerview.chitchat.service.UserService;
import com.learnerview.chitchat.service.RateLimitingService;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final RateLimitingService rateLimitingService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserService userService, 
                        RateLimitingService rateLimitingService,
                        AuthenticationManager authenticationManager,
                        JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.rateLimitingService = rateLimitingService;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        // Rate limiting by IP
        String clientIp = request.getRemoteAddr();
        ConsumptionProbe probe = rateLimitingService.resolveBucket(clientIp).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please try again later.");
        }

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String jwt = tokenProvider.generateToken(authentication);
        
        User user = userService.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        AuthResponse authResponse = new AuthResponse(jwt, user.getUsername(), user.getDisplayName());
        
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest, 
                                    HttpServletRequest request) {
        // Rate limiting by IP
        String clientIp = request.getRemoteAddr();
        ConsumptionProbe probe = rateLimitingService.resolveBucket(clientIp).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please try again later.");
        }
        
        if (userService.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setDisplayName(registerRequest.getDisplayName());
        user.setBio(registerRequest.getBio());
        
        userService.register(user);
        
        return ResponseEntity.ok("Registration successful. Please login.");
    }
}
