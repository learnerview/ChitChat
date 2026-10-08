package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.security.JwtTokenProvider;
import com.learnerview.chitchat.common.security.WsTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * First handshake gate: authenticates the upgrade and enforces the per-user
 * connection cap. Browser clients authenticate with a single-use
 * {@code ?ticket=} (issued by {@code POST /api/auth/ws-ticket}); non-browser
 * clients may use the {@code Authorization: Bearer} header. The resolved
 * userId/tenantId are stored in the session attributes for downstream
 * interceptors and the handshake handler. Anything else is rejected with 401
 * before a session is created.
 */
@Component
public class ConnectionLimitHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "chitchat.userId";
    public static final String TICKET_TENANT_ATTRIBUTE = "chitchat.ticketTenantId";

    private final JwtTokenProvider tokenProvider;
    private final WsTicketService ticketService;
    private final RealtimeConnectionRegistry connectionRegistry;

    public ConnectionLimitHandshakeInterceptor(JwtTokenProvider tokenProvider,
                                               WsTicketService ticketService,
                                               RealtimeConnectionRegistry connectionRegistry) {
        this.tokenProvider = tokenProvider;
        this.ticketService = ticketService;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String userId = null;

        String ticket = extractParameter(request, "ticket");
        if (StringUtils.hasText(ticket)) {
            var wsTicket = ticketService.consume(ticket).orElse(null);
            if (wsTicket != null) {
                userId = wsTicket.userId();
                attributes.put(TICKET_TENANT_ATTRIBUTE, wsTicket.tenantId());
            }
        } else {
            String token = extractBearerToken(request);
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                userId = tokenProvider.getUserIdFromToken(token);
            }
        }

        if (userId == null) {
            // No anonymous realtime sessions: reject before a session is created.
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (!connectionRegistry.isUnderLimit(userId)) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }

    private String extractBearerToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authorization = servletRequest.getServletRequest().getHeader("Authorization");
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
        }
        return null;
    }

    private String extractParameter(ServerHttpRequest request, String name) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter(name);
        }
        return null;
    }
}
