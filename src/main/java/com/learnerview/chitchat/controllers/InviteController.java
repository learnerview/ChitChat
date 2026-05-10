package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.dto.AcceptInviteRequest;
import com.learnerview.chitchat.dto.GenerateInviteRequest;
import com.learnerview.chitchat.entities.InviteLink;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.service.InviteLinkService;
import com.learnerview.chitchat.service.MembershipService;
import com.learnerview.chitchat.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteLinkService inviteLinkService;
    private final MembershipService membershipService;
    private final UserService userService;

    public InviteController(InviteLinkService inviteLinkService, MembershipService membershipService, UserService userService) {
        this.inviteLinkService = inviteLinkService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateInviteLink(Authentication authentication,
                                                 @jakarta.validation.Valid @RequestBody GenerateInviteRequest request) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String tenantId = request.getTenantId();
        Integer days = request.getExpiresInDays();

        // Verify user is owner/admin
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER") &&
            !membershipService.hasRole(tenantId, user.getId(), "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners/admins can create invites");
        }

        LocalDateTime expiresAt = null;
        if (days != null && days > 0) {
            expiresAt = LocalDateTime.now().plusDays(days);
        }

        InviteLink invite = inviteLinkService.createInviteLink(tenantId, user.getId(), expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "token", invite.getToken(),
                "expiresAt", invite.getExpiresAt()
        ));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> getInvites(Authentication authentication, @PathVariable String tenantId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is owner/admin
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER") &&
            !membershipService.hasRole(tenantId, user.getId(), "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners/admins can view invites");
        }

        List<InviteLink> invites = inviteLinkService.getTenantInvites(tenantId);
        return ResponseEntity.ok(invites);
    }

    @PostMapping("/accept")
    public ResponseEntity<?> acceptInvite(Authentication authentication, 
                                          @jakarta.validation.Valid @RequestBody AcceptInviteRequest request) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = request.getToken();

        if (!inviteLinkService.isValidInvite(token)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invite link is invalid or expired");
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            inviteLinkService.acceptInvite(token, user.getId());
            return ResponseEntity.ok("Successfully joined workspace");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{tenantId}/{token}")
    public ResponseEntity<?> revokeInvite(Authentication authentication,
                                          @PathVariable String tenantId,
                                          @PathVariable String token) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is owner
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners can revoke invites");
        }

        try {
            inviteLinkService.revokeInvite(token);
            return ResponseEntity.ok("Invite revoked");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
