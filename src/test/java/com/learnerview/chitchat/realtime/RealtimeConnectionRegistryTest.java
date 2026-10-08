package com.learnerview.chitchat.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeConnectionRegistryTest {

    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(2);

    @Test
    void countsConnectionsPerUser() {
        registry.onConnected(connected("s1", "u1"));
        registry.onConnected(connected("s2", "u1"));

        assertThat(registry.isUnderLimit("u1")).isFalse();
        assertThat(registry.isUnderLimit("u2")).isTrue();
    }

    @Test
    void duplicateConnectedEventForSameSessionDoesNotDoubleCount() {
        registry.onConnected(connected("s1", "u1"));
        registry.onConnected(connected("s1", "u1"));

        // One real connection still leaves headroom under the cap of 2.
        assertThat(registry.isUnderLimit("u1")).isTrue();
    }

    @Test
    void disconnectReleasesTheConnection() {
        registry.onConnected(connected("s1", "u1"));
        registry.onConnected(connected("s2", "u1"));
        assertThat(registry.isUnderLimit("u1")).isFalse();

        registry.onDisconnected(disconnected("s1"));

        assertThat(registry.isUnderLimit("u1")).isTrue();
    }

    @Test
    void unknownSessionDisconnectIsIgnored() {
        registry.onDisconnected(disconnected("ghost"));

        assertThat(registry.isUnderLimit("u1")).isTrue();
    }

    @Test
    void nullUserIsRejectedByTheHandshakeCheck() {
        assertThat(registry.isUnderLimit(null)).isFalse();
    }

    private SessionConnectedEvent connected(String sessionId, String userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Principal principal = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        return new SessionConnectedEvent(new Object(), message, principal);
    }

    private SessionDisconnectEvent disconnected(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(new Object(), message, sessionId, CloseStatus.NORMAL);
    }
}
