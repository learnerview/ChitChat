package com.learnerview.chitchat.common.security;

import com.learnerview.chitchat.common.tenancy.TenantHeaderFilter;
import com.learnerview.chitchat.realtime.ConnectionLimitHandshakeInterceptor;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import com.learnerview.chitchat.user.UserRepository;
import com.learnerview.chitchat.user.UserStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Authenticates the WebSocket handshake. The principal is keyed by userId,
 * matching the HTTP security context. Identity comes from the attributes
 * resolved by {@link ConnectionLimitHandshakeInterceptor} (single-use ticket
 * or bearer JWT); this handler re-validates tenant binding and membership.
 */
@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtTokenProvider tokenProvider;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;

    public JwtHandshakeHandler(JwtTokenProvider tokenProvider,
                               TenantMemberRepository tenantMemberRepository,
                               UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String userId = (String) attributes.get(ConnectionLimitHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (userId == null || !(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        String ticketTenant = (String) attributes.get(
                ConnectionLimitHandshakeInterceptor.TICKET_TENANT_ATTRIBUTE);
        if (ticketTenant == null) {
            // Bearer-token handshake: the token's tenant must match the header.
            String token = extractBearerToken(servletRequest);
            if (!StringUtils.hasText(token)) {
                return null;
            }
            String tokenTenant = tokenProvider.getTenantIdFromToken(token);
            String headerTenant = servletRequest.getServletRequest().getHeader(TenantHeaderFilter.TENANT_HEADER);
            if (!StringUtils.hasText(headerTenant)) {
                headerTenant = servletRequest.getServletRequest().getParameter("tenantId");
            }
            if (StringUtils.hasText(headerTenant) && StringUtils.hasText(tokenTenant)
                    && !headerTenant.trim().equals(tokenTenant)) {
                return null;
            }
            if (userRepository.findById(userId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .isEmpty()) {
                return null;
            }
        }

        // Removed workspace members must not open a realtime stream while their
        // token or ticket is still usable.
        String effectiveTenant = StringUtils.hasText(ticketTenant) ? ticketTenant.trim() : null;
        if (effectiveTenant == null) {
            String headerTenant = servletRequest.getServletRequest().getHeader(TenantHeaderFilter.TENANT_HEADER);
            effectiveTenant = StringUtils.hasText(headerTenant) ? headerTenant.trim() : null;
        }
        if (effectiveTenant == null) {
            effectiveTenant = servletRequest.getServletRequest().getParameter("tenantId");
        }
        if (StringUtils.hasText(effectiveTenant)
                && tenantMemberRepository.findByTenantIdAndUserId(effectiveTenant, userId)
                        .filter(TenantMember::isActive)
                        .isEmpty()) {
            return null;
        }

        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private String extractBearerToken(ServletServerHttpRequest request) {
        String authorization = request.getServletRequest().getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
