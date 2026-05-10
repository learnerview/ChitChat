package com.learnerview.chitchat.security;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.repositories.ConversationRepository;
import com.learnerview.chitchat.repositories.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern CONVERSATION_TOPIC_PATTERN = Pattern.compile("^/topic/conversations/([^/]+)$");

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public WebSocketSubscriptionInterceptor(ConversationRepository conversationRepository, UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
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
            String destination = accessor.getDestination();
            if (destination == null) {
                return message;
            }

            Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination);
            if (!matcher.matches()) {
                return message;
            }

            String conversationId = matcher.group(1);
            String tenantId = getTenantId(accessor.getSessionAttributes());
            if (tenantId == null || tenantId.isBlank()) {
                throw new AccessDeniedException("Missing tenant context");
            }

            String username = principal.getName();
            String userId = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AccessDeniedException("User not found"))
                    .getId();
            
            Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                    .orElseThrow(() -> new AccessDeniedException("Conversation not found"));

            if (!conversation.getParticipantIds().contains(userId)) {
                throw new AccessDeniedException("Not allowed to subscribe to this conversation");
            }
        }

        return message;
    }

    private String getTenantId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        Object value = sessionAttributes.get("tenantId");
        return value == null ? null : value.toString();
    }
}
