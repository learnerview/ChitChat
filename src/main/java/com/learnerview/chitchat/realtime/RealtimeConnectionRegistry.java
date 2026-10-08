package com.learnerview.chitchat.realtime;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks live WebSocket sessions per user so connection limits can be enforced
 * at handshake time. Counting is keyed by session id so connect/disconnect
 * pairs never leak; the handshake check is a read (soft limit - simultaneous
 * handshakes may briefly exceed it by a small margin).
 */
@Component
public class RealtimeConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConnectionRegistry.class);

    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> connectionsPerUser = new ConcurrentHashMap<>();

    private final int maxConnectionsPerUser;

    public RealtimeConnectionRegistry(
            @Value("${app.realtime.max-connections-per-user:5}") int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        Principal user = event.getUser();
        if (sessionId == null || user == null || user.getName() == null) {
            return;
        }
        String userId = user.getName();
        String previous = sessionToUser.put(sessionId, userId);
        if (previous == null) {
            int count = connectionsPerUser
                    .computeIfAbsent(userId, id -> new AtomicInteger())
                    .incrementAndGet();
            if (count > maxConnectionsPerUser) {
                log.warn("User {} exceeded connection limit: {} (max {})",
                        userId, count, maxConnectionsPerUser);
            }
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        String userId = sessionToUser.remove(sessionId);
        if (userId == null) {
            return;
        }
        connectionsPerUser.computeIfPresent(userId, (id, counter) -> {
            int remaining = counter.decrementAndGet();
            return remaining <= 0 ? null : counter;
        });
    }

    /** Whether the user is at or below the configured connection cap. */
    public boolean isUnderLimit(String userId) {
        if (userId == null) {
            return false;
        }
        AtomicInteger count = connectionsPerUser.get(userId);
        return count == null || count.get() < maxConnectionsPerUser;
    }

    @PreDestroy
    void reset() {
        sessionToUser.clear();
        connectionsPerUser.clear();
    }
}
