package com.learnerview.chitchat.webhook;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.tenant.TenantMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String PUBLIC_URL = "https://93.184.216.34/hooks/incoming";

    @Mock
    private WebhookSubscriptionRepository repository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registerRequiresWorkspaceAdminRole() {
        when(authorizationService.requireTenantRole(TENANT,
                TenantMember.Role.OWNER, TenantMember.Role.ADMIN))
                .thenThrow(new ApiException(ErrorCode.INSUFFICIENT_ROLE));

        assertThatThrownBy(() -> webhookService.register(PUBLIC_URL, Set.of("message.sent"), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_ROLE);

        verify(repository, never()).save(any(WebhookSubscription.class));
    }

    @Test
    void registerRejectsPrivateAddresses() {
        stubAdmin();

        assertThatThrownBy(() -> webhookService.register(
                "http://169.254.169.254/latest/meta-data/", Set.of("message.sent"), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).save(any(WebhookSubscription.class));
    }

    @Test
    void registerPersistsAValidatedSubscription() {
        stubAdmin();
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> {
                    WebhookSubscription subscription = invocation.getArgument(0);
                    subscription.setId("sub-1");
                    return subscription;
                });

        var response = webhookService.register(PUBLIC_URL, Set.of("message.sent"), "s3cret");

        assertThat(response.url()).isEqualTo(PUBLIC_URL);
        verify(repository).save(any(WebhookSubscription.class));
    }

    @Test
    void deleteRequiresWorkspaceAdminRole() {
        when(authorizationService.requireTenantRole(TENANT,
                TenantMember.Role.OWNER, TenantMember.Role.ADMIN))
                .thenThrow(new ApiException(ErrorCode.INSUFFICIENT_ROLE));

        assertThatThrownBy(() -> webhookService.delete("sub-1"))
                .isInstanceOf(ApiException.class);

        verify(repository, never()).findByIdAndTenantId(anyString(), anyString());
    }

    @Test
    void updateAppliesPartialChanges() {
        stubAdmin();
        WebhookSubscription existing = WebhookSubscription.builder()
                .id("sub-1").tenantId(TENANT).url(PUBLIC_URL)
                .events(Set.of("message.sent")).active(true)
                .build();
        when(repository.findByIdAndTenantId("sub-1", TENANT)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(WebhookSubscription.class))).thenAnswer(i -> i.getArgument(0));

        var response = webhookService.update("sub-1", null, Set.of("message.deleted"), false);

        assertThat(response.events()).containsExactly("message.deleted");
        assertThat(response.active()).isFalse();
        assertThat(response.url()).isEqualTo(PUBLIC_URL);
    }

    @Test
    void updateRejectsPrivateUrl() {
        stubAdmin();
        WebhookSubscription existing = WebhookSubscription.builder()
                .id("sub-1").tenantId(TENANT).url(PUBLIC_URL)
                .events(Set.of("message.sent")).active(true)
                .build();
        when(repository.findByIdAndTenantId("sub-1", TENANT)).thenReturn(java.util.Optional.of(existing));

        assertThatThrownBy(() -> webhookService.update(
                "sub-1", "http://10.0.0.1/internal", null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).save(any(WebhookSubscription.class));
    }

    @Test
    void updateRejectsEmptyRequest() {
        stubAdmin();

        assertThatThrownBy(() -> webhookService.update("sub-1", null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).findByIdAndTenantId(anyString(), anyString());
    }

    private void stubAdmin() {
        when(authorizationService.requireTenantRole(TENANT,
                TenantMember.Role.OWNER, TenantMember.Role.ADMIN))
                .thenReturn(TenantMember.builder()
                        .tenantId(TENANT).userId("user-1").role("ADMIN").build());
    }
}
