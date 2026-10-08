package com.learnerview.chitchat.authorization;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.conversation.ConversationRepository;
import com.learnerview.chitchat.conversation.ConversationType;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";
    private static final String CONVERSATION = "conv-1";

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private com.learnerview.chitchat.tenant.TenantRepository tenantRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsWhenUserIsNotATenantMember() {
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.requireTenantMember(TENANT))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);
    }

    @Test
    void rejectsWhenConversationDoesNotExist() {
        when(conversationRepository.findByIdAndTenantId(CONVERSATION, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.requireConversationAccess(CONVERSATION))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void rejectsWhenMembershipWasRevoked() {
        when(conversationRepository.findByIdAndTenantId(CONVERSATION, TENANT))
                .thenReturn(Optional.of(conversation()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER").joinedAt(LocalDateTime.now()).build()));
        when(conversationMemberRepository.findByConversationIdAndUserId(CONVERSATION, USER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.requireConversationAccess(CONVERSATION))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);
    }

    @Test
    void grantsAccessToActiveMember() {
        when(conversationRepository.findByIdAndTenantId(CONVERSATION, TENANT))
                .thenReturn(Optional.of(conversation()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER").joinedAt(LocalDateTime.now()).build()));
        when(conversationMemberRepository.findByConversationIdAndUserId(CONVERSATION, USER))
                .thenReturn(Optional.of(ConversationMember.builder()
                        .tenantId(TENANT).conversationId(CONVERSATION).userId(USER)
                        .role(ConversationMember.Role.MEMBER)
                        .joinedAt(LocalDateTime.now()).build()));

        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(CONVERSATION);

        assertThat(access.userId()).isEqualTo(USER);
        assertThat(access.conversation().getId()).isEqualTo(CONVERSATION);
        assertThat(access.member().getRole()).isEqualTo(ConversationMember.Role.MEMBER);
    }

    @Test
    void rejectsWhenMembershipRowIsSoftRemoved() {
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER")
                        .joinedAt(LocalDateTime.now())
                        .removedAt(LocalDateTime.now())
                        .build()));

        assertThatThrownBy(() -> authorizationService.requireTenantMember(TENANT))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);
    }

    @Test
    void rejectsEveryoneWhenTheWorkspaceIsDeleted() {
        when(tenantRepository.findById(TENANT))
                .thenReturn(Optional.of(com.learnerview.chitchat.tenant.Tenant.builder()
                        .id(TENANT).name("W").slug("w").active(false).build()));

        assertThatThrownBy(() -> authorizationService.requireTenantMember(TENANT))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);
    }

    @Test
    void currentUserIdComesFromSecurityContext() {
        assertThat(authorizationService.currentUserId()).isEqualTo(USER);
    }

    @Test
    void rejectsAnonymousCaller() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authorizationService.currentUserId())
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setTenantId(TENANT);
        conversation.setType(ConversationType.GROUP);
        conversation.setCreatedBy(USER);
        return conversation;
    }
}
