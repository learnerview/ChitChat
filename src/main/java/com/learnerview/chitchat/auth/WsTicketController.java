package com.learnerview.chitchat.auth;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.security.WsTicketService;
import com.learnerview.chitchat.common.tenancy.TenantHeaderFilter;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Map;

/**
 * Issues short-lived single-use tickets for WebSocket handshakes, so browser
 * clients never need to place their access JWT in a URL. The tenant comes
 * from the header (not TenantContext): /api/auth/** is exempt from the tenant
 * filter so cross-tenant auth endpoints work before a workspace is chosen.
 */
@RestController
@RequestMapping("/api/auth")
public class WsTicketController {

    private final WsTicketService ticketService;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    public WsTicketController(WsTicketService ticketService, AuthorizationService authorizationService,
                              UserRepository userRepository) {
        this.ticketService = ticketService;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/ws-ticket")
    public Map<String, Object> issueTicket(
            @RequestHeader(TenantHeaderFilter.TENANT_HEADER) String tenantId) {
        authorizationService.requireTenantMember(tenantId);
        String userId = authorizationService.currentUserId();
        User user = userRepository.findById(userId).orElse(null);
        Long passwordStamp = user == null || user.getPasswordChangedAt() == null ? null
                : user.getPasswordChangedAt().atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
        String ticket = ticketService.issue(userId, tenantId, passwordStamp);
        return Map.of("ticket", ticket, "expiresIn", ticketService.ttlSeconds());
    }
}
