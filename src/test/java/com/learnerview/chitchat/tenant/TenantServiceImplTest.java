package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.webhook.WebhookSubscription;
import com.learnerview.chitchat.webhook.WebhookSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String OWNER = "user-1";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private WebhookSubscriptionRepository webhookSubscriptionRepository;

    @Mock
    private InviteLinkRepository inviteLinkRepository;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Test
    void createTenantWritesTheOwnerMembershipAtomically() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(TENANT);
            return tenant;
        });

        Tenant tenant = tenantService.createTenant("Acme", "acme", OWNER, "desc");

        assertThat(tenant.getId()).isEqualTo(TENANT);
        org.mockito.ArgumentCaptor<TenantMember> captor =
                org.mockito.ArgumentCaptor.forClass(TenantMember.class);
        verify(tenantMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("OWNER");
        assertThat(captor.getValue().getUserId()).isEqualTo(OWNER);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void createTenantCleansUpTheWorkspaceWhenOwnerMembershipFails() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(TENANT);
            return tenant;
        });
        when(tenantMemberRepository.save(any(TenantMember.class)))
                .thenThrow(new RuntimeException("mongo down"));

        assertThatThrownBy(() -> tenantService.createTenant("Acme", "acme", OWNER, "desc"))
                .hasMessage("mongo down");

        verify(tenantRepository).deleteById(TENANT);
    }

    @Test
    void deleteTenantDeactivatesWebhooksAndRevokesInvites() {
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT).name("W").slug("w").active(true).build()));
        WebhookSubscription subscription = WebhookSubscription.builder()
                .id("sub-1").tenantId(TENANT).url("https://93.184.216.34/hook").active(true).build();
        when(webhookSubscriptionRepository.findByTenantId(TENANT)).thenReturn(List.of(subscription));
        InviteLink invite = InviteLink.builder().tenantId(TENANT).token("tok").build();
        when(inviteLinkRepository.findByTenantId(TENANT)).thenReturn(List.of(invite));

        tenantService.deleteTenant(TENANT);

        assertThat(subscription.isActive()).isFalse();
        verify(webhookSubscriptionRepository).save(subscription);
        verify(inviteLinkRepository).delete(invite);
    }
}
