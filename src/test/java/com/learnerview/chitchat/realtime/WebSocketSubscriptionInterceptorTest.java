package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.conversation.ConversationRepository;
import com.learnerview.chitchat.conversation.ConversationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionInterceptorTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";
    private static final String CONVERSATION = "conv-1";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private com.learnerview.chitchat.tenant.TenantMemberRepository tenantMemberRepository;

    private WebSocketSubscriptionInterceptor interceptor;

    private final Principal principal = new UsernamePasswordAuthenticationToken(USER, null, List.of());

    @BeforeEach
    void setUp() {
        // max 2 subscriptions per session, max 100 byte payloads
        interceptor = new WebSocketSubscriptionInterceptor(
                conversationRepository, memberRepository, tenantMemberRepository, 2, 100);
    }

    @Test
    void rejectsUnauthenticatedSendAndSubscribe() {
        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SEND, "/app/conversations/c1/resume", null, session(), "{}"), null))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/user/queue/sync", null, session(), null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsAuthenticatedUserQueueSubscription() {
        Message<?> message = stomp(StompCommand.SUBSCRIBE, "/user/queue/sync", principal, session(), null);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void rejectsUnknownDestinations() {
        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/global", principal, session(), null), null))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SEND, "/app/whatever", principal, session(), "x"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void conversationTopicRequiresMembership() {
        Map<String, Object> attributes = session();
        when(conversationRepository.findByIdAndTenantId(CONVERSATION, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                        principal, attributes, null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activeMemberMaySubscribeToConversationTopic() {
        stubMembership(CONVERSATION);

        Message<?> message = stomp(StompCommand.SUBSCRIBE,
                "/topic/conversations/" + CONVERSATION, principal, session(), null);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void leftMemberMayNotSubscribe() {
        stubMembership(CONVERSATION);
        ConversationMember left = activeMember();
        left.setLeftAt(LocalDateTime.now());
        when(memberRepository.findByConversationIdAndUserId(CONVERSATION, USER))
                .thenReturn(Optional.of(left));

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                        principal, session(), null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removedWorkspaceMemberMayNotSubscribe() {
        when(conversationRepository.findByIdAndTenantId(CONVERSATION, TENANT))
                .thenReturn(Optional.of(conversation()));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(com.learnerview.chitchat.tenant.TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER")
                        .joinedAt(LocalDateTime.now())
                        .removedAt(LocalDateTime.now())
                        .build()));

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                        principal, session(), null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingTenantContextBlocksConversationSubscription() {
        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                        principal, new HashMap<>(), null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscriptionCapIsEnforcedAndUnsubscribeFreesASlot() {
        Map<String, Object> attributes = session();
        stubMembership(CONVERSATION);
        stubMembership("conv-2");

        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/user/queue/sync", principal, attributes, null), null);
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                principal, attributes, null), null);

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SUBSCRIBE, "/topic/conversations/conv-2",
                        principal, attributes, null), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Subscription limit");

        // Re-subscribing to an already-counted destination stays within the cap.
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/user/queue/sync", principal, attributes, null), null);

        // Freeing a slot makes room for the previously rejected subscription.
        interceptor.preSend(stomp(StompCommand.UNSUBSCRIBE, "/user/queue/sync", principal, attributes, null), null);
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/conversations/conv-2",
                principal, attributes, null), null);
    }

    @Test
    void unsubscribeBySubscriptionIdFreesTheSlot() {
        Map<String, Object> attributes = session();
        stubMembership(CONVERSATION);
        stubMembership("conv-2");

        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/user/queue/sync", principal,
                attributes, null, "sub-1"), null);
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/conversations/" + CONVERSATION,
                principal, attributes, null, "sub-2"), null);

        // An UNSUBSCRIBE frame typically carries only the subscription id.
        interceptor.preSend(stomp(StompCommand.UNSUBSCRIBE, null, principal,
                attributes, null, "sub-1"), null);

        // The freed slot accepts conv-2 without hitting the cap.
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, "/topic/conversations/conv-2",
                principal, attributes, null), null);
    }

    @Test
    void oversizedSendPayloadIsRejected() {
        byte[] payload = "x".repeat(101).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> interceptor.preSend(
                stomp(StompCommand.SEND, "/app/conversations/" + CONVERSATION + "/resume",
                        principal, session(), payload), null))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("maximum size");
    }

    @Test
    void sendToResumeDestinationIsAllowedAndMessageDestinationIsRejected() {
        Message<?> resume = stomp(StompCommand.SEND,
                "/app/conversations/" + CONVERSATION + "/resume", principal, session(), "{}");
        Message<?> legacy = stomp(StompCommand.SEND,
                "/app/conversations/" + CONVERSATION + "/messages", principal, session(), "hi");

        assertThat(interceptor.preSend(resume, null)).isSameAs(resume);
        assertThatThrownBy(() -> interceptor.preSend(legacy, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<?> stomp(StompCommand command, String destination, Principal user,
                             Map<String, Object> attributes, Object payload) {
        return stomp(command, destination, user, attributes, payload, null);
    }

    private Message<?> stomp(StompCommand command, String destination, Principal user,
                             Map<String, Object> attributes, Object payload, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        if (attributes != null) {
            accessor.setSessionAttributes(attributes);
        }
        if (subscriptionId != null) {
            accessor.setSubscriptionId(subscriptionId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload == null ? new byte[0] : payload,
                accessor.getMessageHeaders());
    }

    private void stubMembership(String conversationId) {
        Conversation conversation = conversation();
        conversation.setId(conversationId);
        when(conversationRepository.findByIdAndTenantId(conversationId, TENANT))
                .thenReturn(Optional.of(conversation));
        ConversationMember member = activeMember();
        member.setConversationId(conversationId);
        when(memberRepository.findByConversationIdAndUserId(conversationId, USER))
                .thenReturn(Optional.of(member));
        when(tenantMemberRepository.findByTenantIdAndUserId(TENANT, USER))
                .thenReturn(Optional.of(com.learnerview.chitchat.tenant.TenantMember.builder()
                        .tenantId(TENANT).userId(USER).role("MEMBER")
                        .joinedAt(LocalDateTime.now()).build()));
    }

    private Map<String, Object> session() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tenantId", TENANT);
        return attributes;
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setTenantId(TENANT);
        conversation.setType(ConversationType.GROUP);
        return conversation;
    }

    private ConversationMember activeMember() {
        return ConversationMember.builder()
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .userId(USER)
                .role(ConversationMember.Role.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();
    }
}
