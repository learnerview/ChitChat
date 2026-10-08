package com.learnerview.chitchat.message;

import com.learnerview.chitchat.auth.SecurityConfig;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.error.GlobalExceptionHandler;
import com.learnerview.chitchat.common.security.JwtAuthenticationFilter;
import com.learnerview.chitchat.common.security.JwtTokenProvider;
import com.learnerview.chitchat.common.tenancy.TenantHeaderFilter;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import com.learnerview.chitchat.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain (tenant filter -> JWT filter ->
 * authorization) against a controller, without a database.
 */
@WebMvcTest(controllers = MessageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TenantHeaderFilter.class,
        GlobalExceptionHandler.class})
class MessageSecurityIntegrationTest {

    private static final String TENANT = "tenant-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.learnerview.chitchat.auth.MongoUserDetailsService userDetailsService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTenantHeaderIsABadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));
    }

    @Test
    void mismatchedTokenTenantIsRejected() throws Exception {
        stubValidToken();
        when(tokenProvider.getTenantIdFromToken("valid")).thenReturn("other-tenant");

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUsersAreRejectedEvenWithAValidToken() throws Exception {
        stubValidToken();
        User disabled = User.builder().id("user-1").username("u").status(UserStatus.DISABLED).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(disabled));

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stalePasswordStampIsRejected() throws Exception {
        stubValidToken();
        User user = User.builder().id("user-1").username("u")
                .status(UserStatus.ACTIVE)
                .passwordChangedAt(LocalDateTime.now())
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(tokenProvider.getPasswordStampFromToken("valid")).thenReturn(1L);

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void domainAuthorizationFailuresSurfaceAs403() throws Exception {
        stubValidToken();
        when(messageService.getMessages(eq("c1"), isNull(), isNull(), isNull()))
                .thenThrow(new ApiException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void validRequestsPassThroughTheWholeChain() throws Exception {
        stubValidToken();
        when(messageService.getMessages(eq("c1"), isNull(), isNull(), isNull()))
                .thenReturn(new MessagePageResponse(List.of(), false, null));

        mockMvc.perform(get("/api/conversations/c1/messages")
                        .header("Authorization", "Bearer valid")
                        .header(TenantHeaderFilter.TENANT_HEADER, TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    private void stubValidToken() {
        when(tokenProvider.validateToken("valid")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid")).thenReturn("user-1");
        when(tokenProvider.getTenantIdFromToken("valid")).thenReturn(TENANT);
        when(tokenProvider.getPasswordStampFromToken("valid")).thenReturn(null);
        User active = User.builder().id("user-1").username("u").status(UserStatus.ACTIVE).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(active));
    }
}
