package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.service.NotificationService;
import com.learnerview.chitchat.service.ConversationService;
import com.learnerview.chitchat.service.PresenceService;
import com.learnerview.chitchat.entities.ConversationMembership;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private PresenceService presenceService;

    // In-memory cache for push subscriptions (in production, use Redis or database)
    private final Map<String, Object> subscriptionCache = new ConcurrentHashMap<>();

    @Override
    public void sendPushNotification(String username, String title, String message, String type) {
        // In a real implementation, this would integrate with push notification services
        // like Firebase Cloud Messaging, Apple Push Notification Service, etc.
        // For now, we'll send via WebSocket
        
        Notification notification = Notification.builder()
                .username(username)
                .title(title)
                .message(message)
                .type(type)
                .timestamp(System.currentTimeMillis())
                .build();
        
        messagingTemplate.convertAndSend("/topic/user/" + username + "/notifications", notification);
    }

    @Override
    public void sendPushNotification(List<String> usernames, String title, String message, String type) {
        usernames.forEach(username -> sendPushNotification(username, title, message, type));
    }

    @Override
    public void sendNotificationToConversation(String conversationId, String title, String message, String type) {
        List<ConversationMembership> members = conversationService.getConversation(conversationId)
                .getMemberCount() > 0 ? 
                List.of() : // In a real implementation, get actual members
                List.of();
        
        List<String> usernames = members.stream()
                .map(ConversationMembership::getUsername)
                .collect(Collectors.toList());
        
        sendPushNotification(usernames, title, message, type);
    }

    @Override
    public void sendTypingNotification(String conversationId, String username, boolean isTyping) {
        TypingNotification typingNotification = TypingNotification.builder()
                .conversationId(conversationId)
                .username(username)
                .isTyping(isTyping)
                .timestamp(System.currentTimeMillis())
                .build();
        
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId + "/typing", typingNotification);
    }

    @Override
    public void sendOnlineStatusNotification(String username, boolean isOnline) {
        OnlineStatusNotification statusNotification = OnlineStatusNotification.builder()
                .username(username)
                .online(isOnline)
                .timestamp(System.currentTimeMillis())
                .build();
        
        messagingTemplate.convertAndSend("/topic/presence/status", statusNotification);
    }

    @Override
    public void sendNewMessageNotification(String conversationId, String senderUsername, String messageContent) {
        // Get all members of the conversation except the sender
        List<ConversationMembership> members = List.of(); // Get actual members in real implementation
        
        List<String> recipientUsernames = members.stream()
                .filter(m -> !m.getUsername().equals(senderUsername))
                .map(ConversationMembership::getUsername)
                .collect(Collectors.toList());
        
        String title = "New message";
        String message = senderUsername + ": " + (messageContent.length() > 50 ? 
                messageContent.substring(0, 50) + "..." : messageContent);
        
        sendPushNotification(recipientUsernames, title, message, "NEW_MESSAGE");
    }

    @Override
    public void sendReactionNotification(String conversationId, String messageSender, String reactor, String emoji) {
        // Send notification to message sender about the reaction
        String title = "Reaction";
        String message = reactor + " reacted to your message with " + emoji;
        
        sendPushNotification(messageSender, title, message, "REACTION");
    }

    @Override
    public void subscribeToPushNotifications(String username, String subscriptionJson) {
        try {
            // Parse subscription JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object subscription = mapper.readValue(subscriptionJson, Object.class);
            
            // Store subscription in database (in real implementation)
            // For now, store in memory cache
            subscriptionCache.put(username, subscription);
            
            log.info("User {} subscribed to push notifications", username);
            
        } catch (Exception e) {
            log.error("Failed to subscribe user {} to push notifications: {}", username, e.getMessage(), e);
            throw new RuntimeException("Failed to subscribe to push notifications", e);
        }
    }

    @Override
    public void unsubscribeFromPushNotifications(String username) {
        try {
            // Remove subscription from database (in real implementation)
            subscriptionCache.remove(username);
            
            log.info("User {} unsubscribed from push notifications", username);
            
        } catch (Exception e) {
            log.error("Failed to unsubscribe user {} from push notifications: {}", username, e.getMessage(), e);
            throw new RuntimeException("Failed to unsubscribe from push notifications", e);
        }
    }
    
    // DTOs for notifications
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Notification {
        private String username;
        private String title;
        private String message;
        private String type;
        private long timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TypingNotification {
        private String conversationId;
        private String username;
        private boolean isTyping;
        private long timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OnlineStatusNotification {
        private String username;
        private boolean online;
        private long timestamp;
    }
}
