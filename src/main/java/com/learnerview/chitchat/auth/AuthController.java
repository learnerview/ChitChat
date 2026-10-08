package com.learnerview.chitchat.auth;

import com.learnerview.chitchat.auth.dto.LoginRequest;
import com.learnerview.chitchat.auth.dto.RegisterRequest;
import com.learnerview.chitchat.auth.dto.SaasAuthResponse;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.security.JwtTokenProvider;
import com.learnerview.chitchat.tenant.MembershipService;
import com.learnerview.chitchat.tenant.Tenant;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantService;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserService;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

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
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.username(), request.displayName(), request.password());

        try {
            String defaultSlug = request.username().toLowerCase()
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            // createTenant also writes the OWNER membership (with compensation).
            tenantService.createTenant(
                    request.displayName() + "'s Workspace",
                    defaultSlug,
                    user.getId(),
                    "Default workspace");
        } catch (RuntimeException ex) {
            // Never leave an account that can neither log in nor re-register.
            try {
                userService.deleteAccount(user.getId());
            } catch (Exception cleanupFailure) {
                log.warn("Failed to clean up user {} after registration failure: {}",
                        user.getId(), cleanupFailure.getMessage());
            }
            throw ex;
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Registration successful. Default workspace created. Please login.");
    }

    @PostMapping("/login")
    public ResponseEntity<SaasAuthResponse> login(@Valid @RequestBody LoginRequest request,
                                                  @RequestHeader(value = "X-Tenant-Id", required = false)
                                                  String tenantId) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userService.findByUsername(request.username())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        if (memberships.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String activeTenantId;
        if (tenantId != null && !tenantId.isBlank()) {
            if (memberships.stream().noneMatch(m -> m.getTenantId().equals(tenantId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            activeTenantId = tenantId;
        } else {
            activeTenantId = memberships.get(0).getTenantId();
        }

        return ResponseEntity.ok(buildSaaSResponse(user, activeTenantId, memberships));
    }

    @PostMapping("/switch-workspace")
    public ResponseEntity<SaasAuthResponse> switchWorkspace(@RequestHeader("Authorization") String authHeader,
                                                            @RequestParam String tenantId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId = tokenProvider.getUserIdFromToken(token);
        User user = userService.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!membershipService.isMember(tenantId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<TenantMember> memberships = membershipService.getUserMemberships(user.getId());
        SaasAuthResponse response = buildSaaSResponse(user, tenantId, memberships);
        String newJwt = tokenProvider.generateToken(user.getId(), tenantId, user.getUsername(),
                passwordStamp(user));
        return ResponseEntity.ok(new SaasAuthResponse(
                newJwt, response.userId(), response.username(), response.displayName(),
                response.currentTenantId(), response.currentTenantName(), response.tenants()));
    }

    private SaasAuthResponse buildSaaSResponse(User user, String activeTenantId,
                                               List<TenantMember> memberships) {
        Tenant activeTenant = tenantService.getTenantById(activeTenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.WORKSPACE_NOT_FOUND));

        String jwt = tokenProvider.generateToken(user.getId(), activeTenantId, user.getUsername(),
                passwordStamp(user));

        List<SaasAuthResponse.TenantInfo> tenantList = memberships.stream()
                .map(m -> tenantService.getTenantById(m.getTenantId())
                        .filter(Tenant::isActive)
                        .map(t -> new SaasAuthResponse.TenantInfo(t.getId(), t.getName(), t.getSlug(), m.getRole()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new SaasAuthResponse(
                jwt,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                activeTenantId,
                activeTenant.getName(),
                tenantList);
    }

    private static Long passwordStamp(User user) {
        return user.getPasswordChangedAt() == null ? null
                : user.getPasswordChangedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
    }
}
