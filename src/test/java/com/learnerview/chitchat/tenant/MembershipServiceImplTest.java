package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class MembershipServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @InjectMocks
    private MembershipServiceImpl membershipService;

    @Test
    void removalIsASoftDeleteThatKeepsTheRow() {
        TenantMember member = member("MEMBER");
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(member));
        when(conversationMemberRepository.findByTenantIdAndUserIdAndLeftAtIsNull(TENANT, USER))
                .thenReturn(List.of());

        membershipService.removeMember(TENANT, USER);

        assertThat(member.getRemovedAt()).isNotNull();
        assertThat(member.isActive()).isFalse();
        verify(tenantMemberRepository).save(member);
    }

    @Test
    void removalDeactivatesAllConversationMembershipsInTheWorkspace() {
        TenantMember member = member("MEMBER");
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(member));
        ConversationMember conversationMember = ConversationMember.builder()
                .tenantId(TENANT).conversationId("conv-1").userId(USER)
                .role(ConversationMember.Role.MEMBER).joinedAt(LocalDateTime.now()).build();
        when(conversationMemberRepository.findByTenantIdAndUserIdAndLeftAtIsNull(TENANT, USER))
                .thenReturn(List.of(conversationMember));

        membershipService.removeMember(TENANT, USER);

        assertThat(conversationMember.getLeftAt()).isNotNull();
        verify(conversationMemberRepository).save(conversationMember);
    }

    @Test
    void theLastOwnerCannotBeRemoved() {
        TenantMember owner = member("OWNER");
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(owner));
        when(tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(TENANT, "OWNER"))
                .thenReturn(1L);

        assertThatThrownBy(() -> membershipService.removeMember(TENANT, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(tenantMemberRepository, never()).save(any(TenantMember.class));
    }

    @Test
    void anOwnerCanBeRemovedWhenAnotherOwnerRemains() {
        TenantMember owner = member("OWNER");
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(owner));
        when(tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(TENANT, "OWNER"))
                .thenReturn(2L);
        when(conversationMemberRepository.findByTenantIdAndUserIdAndLeftAtIsNull(TENANT, USER))
                .thenReturn(List.of());

        membershipService.removeMember(TENANT, USER);

        assertThat(owner.getRemovedAt()).isNotNull();
        verify(tenantMemberRepository).save(owner);
    }

    @Test
    void removingAnUnknownOrAlreadyRemovedMemberFails() {
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(removedMember()));

        assertThatThrownBy(() -> membershipService.removeMember(TENANT, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void addMemberReinstatesARemovedMembership() {
        TenantMember removed = removedMember();
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(removed));
        when(tenantMemberRepository.save(any(TenantMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantMember result = membershipService.addMember(TENANT, USER, "MEMBER");

        assertThat(result).isSameAs(removed);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getRemovedAt()).isNull();
        verify(tenantMemberRepository).save(removed);
    }

    @Test
    void addMemberRejectsAnAlreadyActiveMember() {
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(member("MEMBER")));

        assertThatThrownBy(() -> membershipService.addMember(TENANT, USER, "MEMBER"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_MEMBER);
    }

    @Test
    void removedMembersAreInvisibleToMembershipQueries() {
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(removedMember()));

        assertThat(membershipService.isMember(TENANT, USER)).isFalse();
        assertThat(membershipService.hasRole(TENANT, USER, "OWNER")).isFalse();
        assertThat(membershipService.getMembership(TENANT, USER)).isEmpty();
    }

    private TenantMember member(String role) {
        return TenantMember.builder()
                .tenantId(TENANT).userId(USER).role(role)
                .joinedAt(LocalDateTime.now()).build();
    }

    private TenantMember removedMember() {
        TenantMember member = member("MEMBER");
        member.setRemovedAt(LocalDateTime.now());
        return member;
    }
}
