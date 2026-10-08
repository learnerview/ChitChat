package com.learnerview.chitchat.common.tenancy;

import com.learnerview.chitchat.realtime.ConnectionLimitHandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketTenantHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // A ticket-authenticated handshake already carries its tenant.
        String tenantId = (String) attributes.get(ConnectionLimitHandshakeInterceptor.TICKET_TENANT_ATTRIBUTE);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getHeaders().getFirst(TenantHeaderFilter.TENANT_HEADER);
        }
        if ((tenantId == null || tenantId.isBlank())
                && request instanceof ServletServerHttpRequest servletRequest) {
            // Browser SockJS clients cannot set custom headers on the handshake.
            tenantId = servletRequest.getServletRequest().getParameter("tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        attributes.put("tenantId", tenantId.trim());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }
}
