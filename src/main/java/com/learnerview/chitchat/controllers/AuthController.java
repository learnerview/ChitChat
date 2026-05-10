package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.AuthResponse;
import com.learnerview.chitchat.dto.LoginRequest;
import com.learnerview.chitchat.dto.RegisterRequest;
import com.learnerview.chitchat.dto.SaasAuthResponse;
import com.learnerview.chitchat.dto.TenantInfo;
import com.learnerview.chitchat.entities.Tenant;
import com.learnerview.chitchat.entities.TenantMember;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.security.JwtTokenProvider;
import com.learnerview.chitchat.service.MembershipService;
import com.learnerview.chitchat.service.TenantService;
import com.learnerview.chitchat.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final TenantService tenantService;
    private final MembershipService membershipService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserService userService,
                        TenantService tenantService,
                        MembershipService membershipService,
                        AuthenticationManager authenticationManager,
                        JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.tenantService = tenantService;
        this.membershipService = membershipService;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userService.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        
        // Create global user
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setDisplayName(registerRequest.getDisplayName());
        User savedUser = userService.register(user);

        // Auto-create default workspace for user
        String defaultSlug = registerRequest.getUsername().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant defaultTenant = tenantService.createTenant(
                registerRequest.getDisplayName() + "'s Workspace",
                defaultSlug,
                savedUser.getId(),
                "Default workspace"
        );

        // Add user as OWNER of default workspace
        membershipService.addMember(defaultTenant.getId(), savedUser.getId(), "OWNER");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                "Registration successful. Default workspace created. Please login."
        );
    }

    @PostMapping("/login")
    public ResponseEntity<SaasAuthResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                                   @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        User user = userService.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get all workspaces user belongs to
        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        
        if (memberships.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        // Determine which tenant to use
        String activeTenantId;
        if (tenantId != null && !tenantId.isBlank()) {
            if (memberships.stream().noneMatch(m -> m.getTenantId().equals(tenantId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            activeTenantId = tenantId;
        } else {
            activeTenantId = memberships.get(0).getTenantId();
        }

        // Generate JWT with user's tenant list
        String jwt = tokenProvider.generateToken(authentication, activeTenantId);

        // Build tenant list response
        List<TenantInfo> tenantList = memberships.stream()
                .map(m -> {
                    Tenant t = tenantService.getTenantById(m.getTenantId())
                            .orElseThrow(() -> new RuntimeException("Tenant not found"));
                    return new TenantInfo(t.getId(), t.getName(), t.getSlug(), m.getRole());
                })
                .toList();

        Tenant activeTenant = tenantService.getTenantById(activeTenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        SaasAuthResponse response = new SaasAuthResponse(
                jwt,
                user.getUsername(),
                user.getDisplayName(),
                activeTenantId,
                activeTenant.getName(),
                tenantList
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/switch-workspace")
    public ResponseEntity<SaasAuthResponse> switchWorkspace(@RequestHeader("Authorization") String authHeader,
                                                             @RequestParam String tenantId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        String username = tokenProvider.getUsernameFromToken(token);
        
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is a member of the target workspace
        if (!membershipService.isMember(tenantId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Generate new JWT for the new workspace
        String newJwt = tokenProvider.generateTokenForUsername(username, tenantId);

        // Get all workspaces
        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        List<TenantInfo> tenantList = memberships.stream()
                .map(m -> {
                    Tenant t = tenantService.getTenantById(m.getTenantId())
                            .orElseThrow(() -> new RuntimeException("Tenant not found"));
                    return new TenantInfo(t.getId(), t.getName(), t.getSlug(), m.getRole());
                })
                .toList();

        Tenant activeTenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        SaasAuthResponse response = new SaasAuthResponse(
                newJwt,
                user.getUsername(),
                user.getDisplayName(),
                tenantId,
                activeTenant.getName(),
                tenantList
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchangeExternalToken(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        if (memberships.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String tenantId = memberships.get(0).getTenantId();
        String jwt = tokenProvider.generateTokenForUsername(user.getUsername(), tenantId);

        return ResponseEntity.ok(new AuthResponse(jwt, user.getUsername(), user.getDisplayName()));
    }
}
