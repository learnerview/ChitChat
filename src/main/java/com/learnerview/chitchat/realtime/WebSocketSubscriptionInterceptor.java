package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.conversation.ConversationRepository;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Authorizes STOMP traffic per session:
 * <ul>
 *   <li>SUBSCRIBE - destination allowlist; conversation topics require active membership</li>
 *   <li>per-session subscription cap</li>
 *   <li>SEND - destination allowlist (resume only) and payload size limit</li>
 * </ul>
 * The principal is the userId established during the handshake.
 */
@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC_PATTERN =
            Pattern.compile("^/topic/conversations/([^/]+)$");
    private static final Pattern SEND_RESUME_PATTERN =
            Pattern.compile("^/app/conversations/([^/]+)/resume$");
    private static final String USER_SYNC_DESTINATION = "/user/queue/sync";
    private static final String SUBSCRIPTIONS_ATTRIBUTE = "subscriptions";
    private static final String SUBSCRIPTION_IDS_ATTRIBUTE = "subscriptionIds";

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final int maxSubscriptionsPerSession;
    private final int maxSendPayloadBytes;

    public WebSocketSubscriptionInterceptor(ConversationRepository conversationRepository,
                                            ConversationMemberRepository memberRepository,
                                            TenantMemberRepository tenantMemberRepository,
                                            @Value("${app.realtime.max-subscriptions-per-session:100}")
                                            int maxSubscriptionsPerSession,
                                            @Value("${app.realtime.max-send-payload-bytes:65536}")
                                            int maxSendPayloadBytes) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.maxSubscriptionsPerSession = maxSubscriptionsPerSession;
        this.maxSendPayloadBytes = maxSendPayloadBytes;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        Principal principal = accessor.getUser();

        if ((command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) && principal == null) {
            throw new AccessDeniedException("Authentication required for WebSocket messaging");
        }

        if (command == StompCommand.SUBSCRIBE) {
            handleSubscribe(accessor, principal);
        } else if (command == StompCommand.UNSUBSCRIBE) {
            handleUnsubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            handleSend(message, accessor);
        }

        return message;
    }

    private void handleSubscribe(StompHeaderAccessor accessor, Principal principal) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Destination is required");
        }

        java.util.regex.Matcher conversationMatcher = CONVERSATION_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
        boolean isConversationTopic = conversationMatcher.matches();
        boolean isUserQueue = USER_SYNC_DESTINATION.equals(destination);
        if (!isConversationTopic && !isUserQueue) {
            throw new AccessDeniedException("Unsupported subscription destination");
        }

        if (isConversationTopic) {
            String conversationId = conversationMatcher.group(1);
            String tenantId = getTenantId(accessor.getSessionAttributes());
            if (tenantId == null || tenantId.isBlank()) {
                throw new AccessDeniedException("Missing tenant context");
            }

            boolean allowed = conversationRepository
                    .findByIdAndTenantId(conversationId, tenantId)
                    .isPresent() && tenantMemberRepository
                    .findByTenantIdAndUserId(tenantId, principal.getName())
                    .filter(TenantMember::isActive)
                    .isPresent() && memberRepository
                    .findByConversationIdAndUserId(conversationId, principal.getName())
                    .filter(ConversationMember::isActive)
                    .isPresent();

            if (!allowed) {
                throw new AccessDeniedException("Not allowed to subscribe to this conversation");
            }
        }

        Set<String> subscriptions = subscriptions(accessor.getSessionAttributes());
        if (!subscriptions.contains(destination) && subscriptions.size() >= maxSubscriptionsPerSession) {
            throw new AccessDeniedException("Subscription limit reached for this session");
        }
        subscriptions.add(destination);

        String subscriptionId = accessor.getSubscriptionId();
        if (subscriptionId != null && accessor.getSessionAttributes() != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> idMap = (Map<String, String>) accessor.getSessionAttributes()
                    .computeIfAbsent(SUBSCRIPTION_IDS_ATTRIBUTE, key -> new ConcurrentHashMap<>());
            idMap.put(subscriptionId, destination);
        }
    }

    private void handleUnsubscribe(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        String destination = accessor.getDestination();
        if (destination == null && accessor.getSubscriptionId() != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> idMap =
                    (Map<String, String>) sessionAttributes.get(SUBSCRIPTION_IDS_ATTRIBUTE);
            if (idMap != null) {
                destination = idMap.remove(accessor.getSubscriptionId());
            }
        }
        if (destination != null) {
            subscriptions(sessionAttributes).remove(destination);
        }
    }

    private void handleSend(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !SEND_RESUME_PATTERN.matcher(destination).matches()) {
            throw new AccessDeniedException("Unsupported send destination");
        }

        int size = payloadSizeBytes(message.getPayload());
        if (size > maxSendPayloadBytes) {
            throw new MessageDeliveryException(message,
                    "Payload exceeds the maximum size of " + maxSendPayloadBytes + " bytes");
        }
    }

    private static int payloadSizeBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes.length;
        }
        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Set<String> subscriptions(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return ConcurrentHashMap.newKeySet();
        }
        return (Set<String>) sessionAttributes.computeIfAbsent(
                SUBSCRIPTIONS_ATTRIBUTE, key -> ConcurrentHashMap.newKeySet());
    }

    private String getTenantId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        Object value = sessionAttributes.get("tenantId");
        return value == null ? null : value.toString();
    }
}
