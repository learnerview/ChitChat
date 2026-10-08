package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.authorization.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteLinkService inviteLinkService;
    private final AuthorizationService authorizationService;

    public InviteController(InviteLinkService inviteLinkService,
                            AuthorizationService authorizationService) {
        this.inviteLinkService = inviteLinkService;
        this.authorizationService = authorizationService;
    }

    public record GenerateInviteRequest(
            @NotBlank(message = "Tenant ID is required") String tenantId,
            Integer expiresInDays
    ) {
    }

    public record AcceptInviteRequest(
            @NotBlank(message = "Token is required") String token
    ) {
    }

    public record InviteResponse(
            String token,
            String tenantId,
            String createdBy,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        static InviteResponse from(InviteLink invite) {
            return new InviteResponse(invite.getToken(), invite.getTenantId(),
                    invite.getCreatedBy(), invite.getExpiresAt(), invite.getCreatedAt());
        }
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse generateInviteLink(@Valid @RequestBody GenerateInviteRequest request) {
        String userId = authorizationService.currentUserId();
        authorizationService.requireTenantRole(request.tenantId(),
                TenantMember.Role.OWNER, TenantMember.Role.ADMIN);

        // Invites always expire: default 7 days, hard cap 365.
        int days = request.expiresInDays() == null ? 7 : Math.min(request.expiresInDays(), 365);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(days);

        return InviteResponse.from(
                inviteLinkService.createInviteLink(request.tenantId(), userId, expiresAt));
    }

    @GetMapping("/{tenantId}")
    public List<InviteResponse> getInvites(@PathVariable String tenantId) {
        authorizationService.requireTenantRole(tenantId,
                TenantMember.Role.OWNER, TenantMember.Role.ADMIN);
        return inviteLinkService.getTenantInvites(tenantId).stream()
                .map(InviteResponse::from)
                .toList();
    }

    @PostMapping("/accept")
    public Map<String, String> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        String userId = authorizationService.currentUserId();
        String tenantId = inviteLinkService.acceptInvite(request.token(), userId);
        return Map.of("status", "joined", "tenantId", tenantId);
    }

    @DeleteMapping("/{tenantId}/{token}")
    public void revokeInvite(@PathVariable String tenantId, @PathVariable String token) {
        authorizationService.requireTenantRole(tenantId, TenantMember.Role.OWNER);
        inviteLinkService.revokeInvite(token, tenantId);
    }
}
