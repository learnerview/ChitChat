package com.learnerview.chitchat.common.security;

import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import com.learnerview.chitchat.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resolves the authenticated user identity for every HTTP request.
 * The JWT subject is the stable userId; the SecurityContext principal is
 * therefore always keyed by id, never by username.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.externalAuth.autoProvisionUser:true}")
    private boolean autoProvisionUser;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            String tenantId = TenantContext.getTenantId();

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String userId = tokenProvider.getUserIdFromToken(jwt);
                String tokenTenant = tokenProvider.getTenantIdFromToken(jwt);

                if (shouldEnforceTenantMatch(request)
                        && tenantId != null
                        && tokenTenant != null
                        && !tenantId.equals(tokenTenant)) {
                    throw new IllegalArgumentException("Token tenant does not match request tenant");
                }

                authenticateById(userId, jwt, request);
            } else if (StringUtils.hasText(jwt) && tokenProvider.isExternalAuthEnabled()
                    && tokenProvider.validateExternalToken(jwt)) {
                String externalUsername = tokenProvider.getExternalUsernameFromToken(jwt);
                String externalUserId = tokenProvider.getExternalUserIdFromToken(jwt);

                if (externalUsername != null && tenantId != null) {
                    User user = ensureProvisionedExternalUser(tenantId, externalUsername, externalUserId);
                    if (user != null) {
                        setAuthentication(user.getId(), request);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateById(String userId, String jwt, HttpServletRequest request) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        userRepository.findById(userId)
                // Disabled/deleted accounts lose access immediately, not at token expiry.
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                // Password change invalidates all previously issued tokens.
                .filter(user -> isPasswordStampCurrent(user, jwt))
                .ifPresent(user -> setAuthentication(user.getId(), request));
    }

    private boolean isPasswordStampCurrent(User user, String jwt) {
        LocalDateTime changedAt = user.getPasswordChangedAt();
        if (changedAt == null) {
            return true; // legacy account without a stamp - until the next password change
        }
        Long stamp = tokenProvider.getPasswordStampFromToken(jwt);
        if (stamp == null) {
            return false;
        }
        long changedAtMillis = changedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return stamp == changedAtMillis;
    }

    private void setAuthentication(String userId, HttpServletRequest request) {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(userId)
                .password("N/A")
                .authorities("USER")
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User ensureProvisionedExternalUser(String tenantId, String username, String externalUserId) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (!autoProvisionUser) {
            return null;
        }

        User user = User.builder()
                .username(username)
                .displayName(username)
                .externalUserId(externalUserId)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean shouldEnforceTenantMatch(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Cross-tenant onboarding endpoints must work before/while switching tenant context.
        if (path.startsWith("/api/auth") || path.startsWith("/api/workspaces") || path.equals("/api/invites/accept")) {
            return false;
        }
        return true;
    }
}
