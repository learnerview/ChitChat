package com.learnerview.chitchat.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantHeaderFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (shouldValidate(request)) {
                String tenantId = request.getHeader(TENANT_HEADER);
                if (tenantId == null || tenantId.isBlank()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Missing X-Tenant-Id header\"}");
                    return;
                }
                TenantContext.setTenantId(tenantId.trim());
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean shouldValidate(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.startsWith("/api/") || path.startsWith("/ws");
    }
}
