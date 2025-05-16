package com.learnerview.chitchat.service;

import java.util.List;

public interface NotificationService {
    
    void sendPushNotification(String username, String title, String message, String type);
    
    void sendPushNotification(List<String> usernames, String title, String message, String type);
    
    void sendNotificationToConversation(String conversationId, String title, String message, String type);
    
    void sendTypingNotification(String conversationId, String username, boolean isTyping);
    
    void sendOnlineStatusNotification(String username, boolean isOnline);
    
    void sendNewMessageNotification(String conversationId, String senderUsername, String messageContent);
    
    void sendReactionNotification(String conversationId, String messageSender, String reactor, String emoji);
    
    void subscribeToPushNotifications(String username, String subscriptionJson);
    
    void unsubscribeFromPushNotifications(String username);
}
