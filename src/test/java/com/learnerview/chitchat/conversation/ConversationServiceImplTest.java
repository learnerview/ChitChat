package com.learnerview.chitchat.conversation;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.event.EventPublisherService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.realtime.RealtimeEvent;
import com.learnerview.chitchat.realtime.RealtimeEventPublisher;
import com.learnerview.chitchat.realtime.RealtimeEventType;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String CONVERSATION = "conv-1";
    private static final String OWNER = "user-1";
    private static final String TARGET = "user-2";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private Conversation conversation;
    private ConversationMember ownerMember;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.learnerview.chitchat.common.tenancy.TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        com.learnerview.chitchat.common.tenancy.TenantContext.setTenantId(TENANT);
        ReflectionTestUtils.setField(conversationService, "maxGroupMembers", 500);

        conversation = Conversation.builder()
                .id(CONVERSATION)
                .tenantId(TENANT)
                .type(ConversationType.GROUP)
                .name("Team")
                .createdBy(OWNER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastMessageSequence(0L)
                .status(ConversationStatus.ACTIVE)
                .build();

        ownerMember = ConversationMember.builder()
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .userId(OWNER)
                .role(ConversationMember.Role.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void addMemberPublishesMemberAddedEvent() {
        stubOwnerAccess();
        when(userRepository.findById(TARGET)).thenReturn(Optional.of(User.builder()
                .id(TARGET).username("target").displayName("Target").build()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, TARGET))
                .thenReturn(Optional.of(tenantMember()));
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, TARGET))
                .thenReturn(Optional.empty());
        when(memberRepository.findByConversationId(CONVERSATION))
                .thenReturn(List.of(ownerMember));

        conversationService.addMember(CONVERSATION, TARGET);

        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.MEMBER_ADDED);
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) captor.getValue().payload();
        assertThat(payload.get("userId")).isEqualTo(TARGET);
    }

    @Test
    void removeMemberPublishesMemberRemovedEvent() {
        stubOwnerAccess();
        ConversationMember target = ConversationMember.builder()
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .userId(TARGET)
                .role(ConversationMember.Role.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, TARGET))
                .thenReturn(Optional.of(target));
        when(memberRepository.findByConversationId(CONVERSATION))
                .thenReturn(List.of(ownerMember, target));

        conversationService.removeMember(CONVERSATION, TARGET);

        assertThat(target.getLeftAt()).isNotNull();
        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.MEMBER_REMOVED);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) captor.getValue().payload();
        assertThat(payload.get("userId")).isEqualTo(TARGET);
        assertThat(payload.get("removedBy")).isEqualTo(OWNER);
    }

    @Test
    void ownerCannotBeRemovedBeforeTransfer() {
        stubOwnerAccess();
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, OWNER))
                .thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> conversationService.removeMember(CONVERSATION, OWNER))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(realtimeEventPublisher, never()).publish(anyString(), any());
        verify(memberRepository, never()).save(any(ConversationMember.class));
    }

    @Test
    void addMemberRejectsWhenTheGroupIsAtTheMemberCap() {
        stubOwnerAccess();
        when(userRepository.findById(TARGET)).thenReturn(Optional.of(User.builder()
                .id(TARGET).username("target").displayName("Target").build()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, TARGET))
                .thenReturn(Optional.of(tenantMember()));
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, TARGET))
                .thenReturn(Optional.empty());
        when(memberRepository.countByConversationIdAndLeftAtIsNull(CONVERSATION))
                .thenReturn(500L);

        assertThatThrownBy(() -> conversationService.addMember(CONVERSATION, TARGET))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(memberRepository, never()).save(any(ConversationMember.class));
        verify(realtimeEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void groupCreationRejectsUnknownUsers() {
        stubCreateAccess();
        when(userRepository.findAllById(Set.of(TARGET))).thenReturn(List.of());

        assertThatThrownBy(() -> conversationService.createGroupConversation("Team", Set.of(TARGET)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void groupCreationRejectsUsersOutsideTheWorkspace() {
        stubCreateAccess();
        when(userRepository.findAllById(Set.of(TARGET)))
                .thenReturn(List.of(User.builder().id(TARGET).username("t").displayName("T").build()));
        when(tenantMemberRepository.findByTenantIdAndUserIdIn(eq(TENANT), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> conversationService.createGroupConversation("Team", Set.of(TARGET)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);

        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void groupCreationCompensatesWhenTheConversationSaveFails() {
        stubCreateAccess();
        when(userRepository.findAllById(Set.of(TARGET)))
                .thenReturn(List.of(User.builder().id(TARGET).username("t").displayName("T").build()));
        when(tenantMemberRepository.findByTenantIdAndUserIdIn(eq(TENANT), any()))
                .thenReturn(List.of(tenantMember()));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new RuntimeException("mongo down"));

        assertThatThrownBy(() -> conversationService.createGroupConversation("Team", Set.of(TARGET)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mongo down");

        verify(conversationRepository).deleteById(anyString());
        verify(mongoTemplate).remove(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(ConversationMember.class));
        verify(realtimeEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void directConversationRaceReusesTheWinnerAndDropsOrphanedMembers() {
        stubCreateAccess();
        when(userRepository.findById(TARGET))
                .thenReturn(Optional.of(User.builder().id(TARGET).username("t").displayName("T").build()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, TARGET))
                .thenReturn(Optional.of(tenantMember()));

        Conversation winner = Conversation.builder()
                .id("winner-conv")
                .tenantId(TENANT)
                .type(ConversationType.DM)
                .createdBy(TARGET)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastMessageSequence(0L)
                .status(ConversationStatus.ACTIVE)
                .build();
        when(conversationRepository.findByTenantIdAndDirectKey(eq(TENANT), anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DuplicateKeyException("directKey race"));
        when(memberRepository.findByConversationId("winner-conv"))
                .thenReturn(List.of(ConversationMember.builder()
                        .tenantId(TENANT).conversationId("winner-conv").userId(OWNER)
                        .role(ConversationMember.Role.MEMBER).joinedAt(LocalDateTime.now()).build()));

        var response = conversationService.createDirectConversation(TARGET);

        assertThat(response.id()).isEqualTo("winner-conv");
        verify(mongoTemplate).remove(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(ConversationMember.class));
    }

    @Test
    void leavePublishesMemberRemovedWithLeftReason() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        // DM conversations let owners leave freely; this one is a group, so
        // move the owner role off the leaving member first.
        ownerMember.setRole(ConversationMember.Role.MEMBER);

        conversationService.leaveConversation(CONVERSATION);

        assertThat(ownerMember.getLeftAt()).isNotNull();
        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.MEMBER_REMOVED);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) captor.getValue().payload();
        assertThat(payload.get("reason")).isEqualTo("left");
    }

    @Test
    void ownerCannotLeaveAGroupWithoutTransferring() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        assertThatThrownBy(() -> conversationService.leaveConversation(CONVERSATION))
                .isInstanceOf(ApiException.class);

        verify(realtimeEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void transferOwnershipKeepsCreatedByAndPublishesRoleChange() {
        stubOwnerAccess();
        ConversationMember newOwner = ConversationMember.builder()
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .userId(TARGET)
                .role(ConversationMember.Role.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, TARGET))
                .thenReturn(Optional.of(newOwner));
        when(memberRepository.findByConversationId(CONVERSATION))
                .thenReturn(List.of(ownerMember, newOwner));

        conversationService.transferOwnership(CONVERSATION, TARGET);

        assertThat(conversation.getCreatedBy()).isEqualTo(OWNER);
        assertThat(ownerMember.getRole()).isEqualTo(ConversationMember.Role.ADMIN);
        assertThat(newOwner.getRole()).isEqualTo(ConversationMember.Role.OWNER);

        // Promote-first ordering: a failure mid-transfer leaves two owners
        // (recoverable) rather than zero owners (unmanageable).
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(memberRepository);
        inOrder.verify(memberRepository).save(newOwner);
        inOrder.verify(memberRepository).save(ownerMember);

        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.MEMBER_ROLE_CHANGED);
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) captor.getValue().payload();
        assertThat(payload.get("userId")).isEqualTo(TARGET);
        assertThat(payload.get("role")).isEqualTo("OWNER");
        assertThat(payload.get("previousOwnerId")).isEqualTo(OWNER);
    }

    @Test
    void deleteConversationRemovesDataAndPublishesEvents() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        conversationService.deleteConversation(CONVERSATION);

        verify(mongoTemplate).remove(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(com.learnerview.chitchat.message.Message.class));
        verify(mongoTemplate).remove(any(org.springframework.data.mongodb.core.query.Query.class),
                eq(ConversationMember.class));
        verify(conversationRepository).deleteById(CONVERSATION);
        verify(eventPublisherService).publish(eq(TENANT), eq("conversation.deleted"), any());
        verify(realtimeEventPublisher).publish(eq(CONVERSATION),
                org.mockito.ArgumentMatchers.<RealtimeEvent>any());
    }

    @Test
    void deleteConversationAllowsEitherDirectMember() {
        conversation.setType(ConversationType.DM);
        ownerMember.setRole(ConversationMember.Role.MEMBER);
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        conversationService.deleteConversation(CONVERSATION);

        verify(conversationRepository).deleteById(CONVERSATION);
    }

    @Test
    void deleteConversationRejectsNonOwnerOfGroup() {
        ownerMember.setRole(ConversationMember.Role.MEMBER);
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        assertThatThrownBy(() -> conversationService.deleteConversation(CONVERSATION))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_CONVERSATION_ROLE);

        verify(conversationRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteConversationRequiresOwnerRole() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenThrow(new ApiException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        assertThatThrownBy(() -> conversationService.deleteConversation(CONVERSATION))
                .isInstanceOf(ApiException.class);

        verify(conversationRepository, never()).deleteById(anyString());
    }

    @Test
    void updateMemberSettingsAppliesOnlyProvidedFields() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));
        when(memberRepository.save(any(ConversationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = conversationService.updateMemberSettings(
                CONVERSATION, true, null, null, ConversationMember.NotificationLevel.MENTIONS);

        assertThat(response.pinned()).isTrue();
        assertThat(response.muted()).isFalse();
        assertThat(response.notificationLevel())
                .isEqualTo(ConversationMember.NotificationLevel.MENTIONS);
        verify(memberRepository).save(ownerMember);
    }

    @Test
    void updateMemberSettingsRejectsEmptyRequest() {
        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));

        assertThatThrownBy(() -> conversationService.updateMemberSettings(
                CONVERSATION, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(memberRepository, never()).save(any(ConversationMember.class));
    }

    private void stubOwnerAccess() {
        when(authorizationService.requireConversationRole(CONVERSATION, ConversationMember.Role.OWNER))
                .thenReturn(new AuthorizationService.ConversationAccess(
                        conversation, ownerMember, OWNER));
    }

    private void stubCreateAccess() {
        when(authorizationService.currentUserId()).thenReturn(OWNER);
        when(authorizationService.requireTenantMember(TENANT))
                .thenReturn(com.learnerview.chitchat.tenant.TenantMember.builder()
                        .tenantId(TENANT).userId(OWNER).role("OWNER")
                        .joinedAt(LocalDateTime.now()).build());
    }

    private com.learnerview.chitchat.tenant.TenantMember tenantMember() {
        return com.learnerview.chitchat.tenant.TenantMember.builder()
                .tenantId(TENANT)
                .userId(TARGET)
                .role("MEMBER")
                .joinedAt(LocalDateTime.now())
                .build();
    }
}
