package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteLinkServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";
    private static final String TOKEN = "token-1";

    @Mock
    private InviteLinkRepository inviteLinkRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private InviteLinkServiceImpl inviteLinkService;

    @Test
    void acceptingAnInviteJoinsTheWorkspace() {
        stubInvite(invite(TENANT, null));
        when(tenantRepository.findById(TENANT))
                .thenReturn(Optional.of(tenant(true)));
        stubClaim(true);

        inviteLinkService.acceptInvite(TOKEN, USER);

        verify(membershipService).addMember(TENANT, USER, "MEMBER");
        verify(inviteLinkRepository).delete(any(InviteLink.class));
    }

    @Test
    void acceptingAnAlreadyRedeemedInviteIsRejected() {
        stubInvite(invite(TENANT, null));
        when(tenantRepository.findById(TENANT))
                .thenReturn(Optional.of(tenant(true)));
        stubClaim(false);

        assertThatThrownBy(() -> inviteLinkService.acceptInvite(TOKEN, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(membershipService, never()).addMember(anyString(), anyString(), anyString());
        verify(inviteLinkRepository, never()).delete(any(InviteLink.class));
    }

    @Test
    void failedMembershipAddReleasesTheClaim() {
        stubInvite(invite(TENANT, null));
        when(tenantRepository.findById(TENANT))
                .thenReturn(Optional.of(tenant(true)));
        stubClaim(true);
        when(membershipService.addMember(TENANT, USER, "MEMBER"))
                .thenThrow(new ApiException(ErrorCode.ALREADY_MEMBER));

        assertThatThrownBy(() -> inviteLinkService.acceptInvite(TOKEN, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_MEMBER);

        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(Update.class), eq(InviteLink.class));
        verify(inviteLinkRepository, never()).delete(any(InviteLink.class));
    }

    @Test
    void acceptingAnInviteForADeletedWorkspaceIsRejected() {
        stubInvite(invite(TENANT, null));
        when(tenantRepository.findById(TENANT))
                .thenReturn(Optional.of(tenant(false)));

        assertThatThrownBy(() -> inviteLinkService.acceptInvite(TOKEN, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);

        verify(membershipService, never()).addMember(anyString(), anyString(), anyString());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(InviteLink.class));
        verify(inviteLinkRepository, never()).delete(any(InviteLink.class));
    }

    @Test
    void acceptingAnExpiredInviteIsRejected() {
        stubInvite(invite(TENANT, LocalDateTime.now().minusDays(1)));

        assertThatThrownBy(() -> inviteLinkService.acceptInvite(TOKEN, USER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(membershipService, never()).addMember(anyString(), anyString(), anyString());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(InviteLink.class));
    }

    @Test
    void revokingRequiresTheTokenToBelongToTheAuthorizedTenant() {
        stubInvite(invite("other-tenant", null));

        assertThatThrownBy(() -> inviteLinkService.revokeInvite(TOKEN, TENANT))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(inviteLinkRepository, never()).delete(any(InviteLink.class));
    }

    @Test
    void revokingDeletesTheInviteWhenTheTenantMatches() {
        InviteLink invite = invite(TENANT, null);
        stubInvite(invite);

        inviteLinkService.revokeInvite(TOKEN, TENANT);

        verify(inviteLinkRepository).delete(invite);
    }

    private void stubInvite(InviteLink invite) {
        when(inviteLinkRepository.findByToken(TOKEN)).thenReturn(Optional.of(invite));
    }

    private void stubClaim(boolean won) {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(InviteLink.class)))
                .thenReturn(UpdateResult.acknowledged(1, won ? 1L : 0L, null));
    }

    private InviteLink invite(String tenantId, LocalDateTime expiresAt) {
        return InviteLink.builder()
                .tenantId(tenantId)
                .createdBy("owner-1")
                .token(TOKEN)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Tenant tenant(boolean active) {
        return Tenant.builder().id(TENANT).name("W").slug("w").active(active).build();
    }
}
