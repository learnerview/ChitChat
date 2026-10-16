package com.learnerview.chitchat.auth;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.security.WsTicketService;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsTicketControllerTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    @Mock
    private WsTicketService ticketService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WsTicketController controller;

    @Test
    void issuesATicketForTheHeaderTenant() {
        when(authorizationService.requireTenantMember(TENANT))
                .thenReturn(TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER")
                        .joinedAt(LocalDateTime.now()).build());
        when(authorizationService.currentUserId()).thenReturn(USER);
        when(userRepository.findById(USER)).thenReturn(Optional.of(User.builder()
                .id(USER).username(USER).displayName("User").build()));
        when(ticketService.issue(USER, TENANT, null)).thenReturn("ticket-123");
        when(ticketService.ttlSeconds()).thenReturn(60L);

        Map<String, Object> response = controller.issueTicket(TENANT);

        assertThat(response.get("ticket")).isEqualTo("ticket-123");
        assertThat(response.get("expiresIn")).isEqualTo(60L);
        verify(ticketService).issue(USER, TENANT, null);
    }
}
