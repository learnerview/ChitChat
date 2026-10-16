package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.security.JwtTokenProvider;
import com.learnerview.chitchat.common.security.WsTicketService;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import com.learnerview.chitchat.user.UserStatus;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.LocalDateTime;
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
    private final UserRepository userRepository;

    public ConnectionLimitHandshakeInterceptor(JwtTokenProvider tokenProvider,
                                               WsTicketService ticketService,
                                               RealtimeConnectionRegistry connectionRegistry,
                                               UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.ticketService = ticketService;
        this.connectionRegistry = connectionRegistry;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String userId = null;
        String token = null;
        Long ticketPasswordStamp = null;

        String ticket = extractParameter(request, "ticket");
        if (StringUtils.hasText(ticket)) {
            var wsTicket = ticketService.consume(ticket).orElse(null);
            if (wsTicket != null) {
                userId = wsTicket.userId();
                ticketPasswordStamp = wsTicket.passwordStamp();
                attributes.put(TICKET_TENANT_ATTRIBUTE, wsTicket.tenantId());
            }
        } else {
            token = extractBearerToken(request);
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                userId = tokenProvider.getUserIdFromToken(token);
            }
        }

        if (userId == null) {
            // No anonymous realtime sessions: reject before a session is created.
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Re-check user state that the JWT signature alone cannot guarantee:
        // disabled/deleted accounts and password changes invalidate access immediately.
        if (!isUserAuthorized(userId, token, ticketPasswordStamp)) {
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

    private boolean isUserAuthorized(String userId, @Nullable String jwt, @Nullable Long ticketPasswordStamp) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> isPasswordStampCurrent(user, jwt, ticketPasswordStamp))
                .isPresent();
    }

    private boolean isPasswordStampCurrent(@Nullable User user, @Nullable String jwt,
                                           @Nullable Long ticketPasswordStamp) {
        if (user == null || user.getPasswordChangedAt() == null) {
            return true; // legacy account without a stamp - until the next password change
        }
        long changedAtMillis = user.getPasswordChangedAt()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        // Ticket-based auth: compare the stamp captured when the ticket was issued.
        // A null stamp means the ticket was issued before any password change existed;
        // if the user now has a password-change timestamp, the ticket predates it and
        // must be rejected.
        if (ticketPasswordStamp != null) {
            return ticketPasswordStamp == changedAtMillis;
        }
        if (changedAtMillis > 0) {
            return false; // ticket predates the user's password change
        }

        // Bearer-token auth: compare the stamp embedded in the JWT.
        if (jwt != null) {
            Long stamp = tokenProvider.getPasswordStampFromToken(jwt);
            if (stamp == null) {
                return false;
            }
            return stamp == changedAtMillis;
        }

        return true;
    }
}
