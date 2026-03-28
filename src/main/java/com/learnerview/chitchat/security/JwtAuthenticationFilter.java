package com.learnerview.chitchat.security;

import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserDetailsService userDetailsService;

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
                String username = tokenProvider.getUsernameFromToken(jwt);
                String tokenTenant = tokenProvider.getTenantIdFromToken(jwt);

                if (tenantId != null && tokenTenant != null && !tenantId.equals(tokenTenant)) {
                    throw new IllegalArgumentException("Token tenant does not match request tenant");
                }

                authenticate(username, request);
            } else if (StringUtils.hasText(jwt) && tokenProvider.isExternalAuthEnabled() && tokenProvider.validateExternalToken(jwt)) {
                String externalUsername = tokenProvider.getExternalUsernameFromToken(jwt);
                String externalUserId = tokenProvider.getExternalUserIdFromToken(jwt);

                if (externalUsername != null && tenantId != null) {
                    ensureProvisionedExternalUser(tenantId, externalUsername, externalUserId);
                    authenticate(externalUsername, request);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void ensureProvisionedExternalUser(String tenantId, String username, String externalUserId) {
        if (userRepository.findByTenantIdAndUsername(tenantId, username).isPresent()) {
            return;
        }

        if (!autoProvisionUser) {
            return;
        }

        User user = User.builder()
                .tenantId(tenantId)
                .username(username)
                .displayName(username)
                .externalUserId(externalUserId)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
