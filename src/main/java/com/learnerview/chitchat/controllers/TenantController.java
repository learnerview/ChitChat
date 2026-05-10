package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Tenant;
import com.learnerview.chitchat.entities.TenantMember;
import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.service.MembershipService;
import com.learnerview.chitchat.service.TenantService;
import com.learnerview.chitchat.service.UserService;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class TenantController {

    private final TenantService tenantService;
    private final MembershipService membershipService;
    private final UserService userService;

    public TenantController(TenantService tenantService, MembershipService membershipService, UserService userService) {
        this.tenantService = tenantService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> createWorkspace(Authentication authentication,
                                              @jakarta.validation.Valid @RequestBody com.learnerview.chitchat.dto.WorkspaceRequest request) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            Tenant tenant = tenantService.createTenant(request.getName(), request.getSlug(), user.getId(), request.getDescription());
            membershipService.addMember(tenant.getId(), user.getId(), "OWNER");

            return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<TenantMember>> listMyWorkspaces(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        return ResponseEntity.ok(memberships);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> getWorkspace(Authentication authentication,
                                          @PathVariable String tenantId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is a member
        if (!membershipService.isMember(tenantId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Tenant tenant = tenantService.getTenantById(tenantId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<?> updateWorkspace(Authentication authentication,
                                              @PathVariable String tenantId,
                                              @jakarta.validation.Valid @RequestBody com.learnerview.chitchat.dto.WorkspaceRequest request) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is owner
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners can update workspace");
        }

        Tenant tenant = tenantService.updateTenant(tenantId, request.getName(), request.getDescription());
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/{tenantId}/members")
    public ResponseEntity<?> getMembers(Authentication authentication, @PathVariable String tenantId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!membershipService.isMember(tenantId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<TenantMember> members = membershipService.getTenantMembers(tenantId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{tenantId}/members/{userId}")
    public ResponseEntity<?> removeMember(Authentication authentication,
                                          @PathVariable String tenantId,
                                          @PathVariable String userId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify requester is owner
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners can remove members");
        }

        membershipService.removeMember(tenantId, userId);
        return ResponseEntity.ok("Member removed");
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<?> deleteWorkspace(Authentication authentication, @PathVariable String tenantId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify user is owner
        if (!membershipService.hasRole(tenantId, user.getId(), "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only owners can delete workspace");
        }

        tenantService.deleteTenant(tenantId);
        return ResponseEntity.ok("Workspace deleted");
    }

    @PostMapping("/{tenantId}/leave")
    public ResponseEntity<?> leaveWorkspace(Authentication authentication, @PathVariable String tenantId) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user is owner (can't leave if owner)
        if (membershipService.hasRole(tenantId, user.getId(), "OWNER")) {
            return ResponseEntity.badRequest().body("Owner cannot leave workspace. Transfer ownership first.");
        }

        membershipService.removeMember(tenantId, user.getId());
        return ResponseEntity.ok("Left workspace");
    }
}
