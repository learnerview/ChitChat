package com.learnerview.chitchat.user;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String USER = "user-1";
    private static final String TENANT = "tenant-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private com.learnerview.chitchat.authorization.AuthorizationService authorizationService;

    @InjectMocks
    private UserServiceImpl userService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        com.learnerview.chitchat.common.tenancy.TenantContext.setTenantId(TENANT);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.learnerview.chitchat.common.tenancy.TenantContext.clear();
    }

    @Test
    void soleWorkspaceOwnerCannotDeleteTheirAccount() {
        when(userRepository.findById(USER)).thenReturn(Optional.of(user()));
        when(tenantMemberRepository.findByUserIdAndRemovedAtIsNull(USER))
                .thenReturn(List.of(membership("OWNER")));
        when(tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(TENANT, "OWNER"))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteAccount(USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void accountDeletionDeactivatesAllMembershipsAndRemovesTheUser() {
        User user = user();
        TenantMember membership = membership("OWNER");
        ConversationMember conversationMember = ConversationMember.builder()
                .tenantId(TENANT).conversationId("conv-1").userId(USER)
                .role(ConversationMember.Role.MEMBER).joinedAt(LocalDateTime.now()).build();

        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
        when(tenantMemberRepository.findByUserIdAndRemovedAtIsNull(USER))
                .thenReturn(List.of(membership));
        when(tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(TENANT, "OWNER"))
                .thenReturn(2L);
        when(conversationMemberRepository.findByTenantIdAndUserIdAndLeftAtIsNull(TENANT, USER))
                .thenReturn(List.of(conversationMember));

        userService.deleteAccount(USER);

        assertThat(membership.getRemovedAt()).isNotNull();
        assertThat(conversationMember.getLeftAt()).isNotNull();
        verify(userRepository).delete(user);
    }

    @Test
    void searchUsersOnlyReturnsMembersOfTheCallersWorkspace() {
        when(authorizationService.requireTenantMember(TENANT))
                .thenReturn(membership("MEMBER"));
        when(tenantMemberRepository.findByTenantIdAndRemovedAtIsNull(TENANT))
                .thenReturn(List.of(membership("MEMBER"), otherMembership("user-2")));
        User peer = User.builder().id("user-2").username("peer").displayName("Peer").build();
        User outsider = User.builder().id("user-3").username("peer2").displayName("Peer Two").build();
        when(userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("peer", "peer"))
                .thenReturn(List.of(peer, outsider));

        List<User> results = userService.searchUsers("peer");

        assertThat(results).containsExactly(peer);
    }

    @Test
    void searchUsersRejectsQueriesThatAreTooShort() {
        assertThat(userService.searchUsers("a")).isEmpty();
        assertThat(userService.searchUsers(" ")).isEmpty();
        verify(userRepository, never())
                .findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(any(), any());
    }

    private TenantMember otherMembership(String userId) {
        return TenantMember.builder()
                .tenantId(TENANT).userId(userId).role("MEMBER")
                .joinedAt(LocalDateTime.now()).build();
    }

    private User user() {
        return User.builder().id(USER).username("u").displayName("U").build();
    }

    private TenantMember membership(String role) {
        return TenantMember.builder()
                .tenantId(TENANT).userId(USER).role(role)
                .joinedAt(LocalDateTime.now()).build();
    }
}
