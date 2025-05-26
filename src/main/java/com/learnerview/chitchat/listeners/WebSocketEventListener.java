package com.learnerview.chitchat.listeners;

import com.learnerview.chitchat.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private UserRepository userRepository;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        
        if (username != null) {
            logger.info("User Connected: " + username);
            updateUserStatus(username, true);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        
        if (username != null) {
            logger.info("User Disconnected: " + username);
            updateUserStatus(username, false);
        }
    }
    
    private void updateUserStatus(String username, boolean online) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setOnline(online);
            user.setLastSeen(java.time.LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
