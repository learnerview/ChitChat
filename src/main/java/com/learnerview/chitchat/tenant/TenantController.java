package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class TenantController {

    private final TenantService tenantService;
    private final MembershipService membershipService;
    private final AuthorizationService authorizationService;

    public TenantController(TenantService tenantService,
                            MembershipService membershipService,
                            AuthorizationService authorizationService) {
        this.tenantService = tenantService;
        this.membershipService = membershipService;
        this.authorizationService = authorizationService;
    }

    public record WorkspaceRequest(
            @NotBlank(message = "Workspace name is required")
            @Size(min = 1, max = 100) String name,
            @NotBlank(message = "Workspace slug is required")
            @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase alphanumeric or hyphen")
            String slug,
            @Size(max = 500) String description
    ) {
    }

    public record WorkspaceResponse(
            String id,
            String name,
            String slug,
            String description,
            String ownerId,
            boolean active,
            LocalDateTime createdAt
    ) {
        static WorkspaceResponse from(Tenant tenant) {
            return new WorkspaceResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
                    tenant.getDescription(), tenant.getOwnerId(), tenant.isActive(), tenant.getCreatedAt());
        }
    }

    @PostMapping
    public WorkspaceResponse createWorkspace(@Valid @RequestBody WorkspaceRequest request) {
        String userId = authorizationService.currentUserId();
        // createTenant also writes the caller's OWNER membership atomically.
        Tenant tenant = tenantService.createTenant(request.name(), request.slug(), userId, request.description());
        return WorkspaceResponse.from(tenant);
    }

    @PutMapping("/{tenantId}")
    public WorkspaceResponse updateWorkspace(@PathVariable String tenantId,
                                             @Valid @RequestBody WorkspaceRequest request) {
        authorizationService.requireTenantRole(tenantId, TenantMember.Role.OWNER);
        return WorkspaceResponse.from(
                tenantService.updateTenant(tenantId, request.name(), request.description()));
    }

    @GetMapping("/{tenantId}/members")
    public List<Map<String, Object>> getMembers(@PathVariable String tenantId) {
        authorizationService.requireTenantMember(tenantId);
        return membershipService.getTenantMembers(tenantId).stream()
                .map(m -> Map.<String, Object>of(
                        "userId", m.getUserId(),
                        "role", m.getRole(),
                        "joinedAt", m.getJoinedAt() == null ? "" : m.getJoinedAt().toString()))
                .toList();
    }

    @DeleteMapping("/{tenantId}/members/{userId}")
    public void removeMember(@PathVariable String tenantId, @PathVariable String userId) {
        authorizationService.requireTenantRole(tenantId, TenantMember.Role.OWNER);
        membershipService.removeMember(tenantId, userId);
    }

    @DeleteMapping("/{tenantId}")
    public void deleteWorkspace(@PathVariable String tenantId) {
        authorizationService.requireTenantRole(tenantId, TenantMember.Role.OWNER);
        tenantService.deleteTenant(tenantId);
    }

    @PostMapping("/{tenantId}/leave")
    public void leaveWorkspace(@PathVariable String tenantId) {
        String userId = authorizationService.currentUserId();
        authorizationService.requireTenantMember(tenantId);
        if (membershipService.hasRole(tenantId, userId, TenantMember.Role.OWNER.name())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Owner cannot leave workspace. Transfer ownership first.");
        }
        membershipService.removeMember(tenantId, userId);
    }
}
