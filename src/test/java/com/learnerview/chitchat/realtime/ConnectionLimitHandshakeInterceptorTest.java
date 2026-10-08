package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.security.JwtTokenProvider;
import com.learnerview.chitchat.common.security.WsTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionLimitHandshakeInterceptorTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private com.learnerview.chitchat.common.security.WsTicketService ticketService;

    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry(5);

    private ConnectionLimitHandshakeInterceptor interceptor;

    private ConnectionLimitHandshakeInterceptor interceptor() {
        if (interceptor == null) {
            interceptor = new ConnectionLimitHandshakeInterceptor(tokenProvider, ticketService, registry);
        }
        return interceptor;
    }

    @Test
    void rejectsHandshakesWithoutAToken() {
        ServletServerHttpResponse response = response();

        boolean allowed = interceptor().beforeHandshake(request(null), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void rejectsHandshakesWithAnInvalidToken() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        ServletServerHttpResponse response = response();

        boolean allowed = interceptor().beforeHandshake(request("bad"), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void allowsValidTokensUnderTheConnectionLimit() {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("good")).thenReturn("user-1");
        ServletServerHttpResponse response = response();

        boolean allowed = interceptor().beforeHandshake(request("good"), response, null, new HashMap<>());

        assertThat(allowed).isTrue();
    }

    @Test
    void rejectsUsersAtTheConnectionLimit() {
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("good")).thenReturn("user-1");
        for (int i = 0; i < 5; i++) {
            registry.onConnected(connectedEvent("session-" + i, "user-1"));
        }
        ServletServerHttpResponse response = response();

        boolean allowed = interceptor().beforeHandshake(request("good"), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void acceptsAValidSingleUseTicket() {
        when(ticketService.consume("t-1"))
                .thenReturn(Optional.of(new WsTicketService.WsTicket("user-1", "tenant-1")));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.setParameter("ticket", "t-1");
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(servletRequest), response(), null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes.get(ConnectionLimitHandshakeInterceptor.USER_ID_ATTRIBUTE)).isEqualTo("user-1");
        assertThat(attributes.get(ConnectionLimitHandshakeInterceptor.TICKET_TENANT_ATTRIBUTE))
                .isEqualTo("tenant-1");
    }

    @Test
    void rejectsAnInvalidTicket() {
        when(ticketService.consume("t-stale")).thenReturn(Optional.empty());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.setParameter("ticket", "t-stale");
        ServletServerHttpResponse response = response();

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(servletRequest), response, null, new HashMap<>());

        assertThat(allowed).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private ServletServerHttpRequest request(String token) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        if (token != null) {
            servletRequest.addHeader("Authorization", "Bearer " + token);
        }
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServletServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    private org.springframework.web.socket.messaging.SessionConnectedEvent connectedEvent(
            String sessionId, String userId) {
        org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor =
                org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
                        org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        org.springframework.messaging.Message<byte[]> message =
                org.springframework.messaging.support.MessageBuilder.createMessage(
                        new byte[0], accessor.getMessageHeaders());
        return new org.springframework.web.socket.messaging.SessionConnectedEvent(this, message,
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        userId, null, java.util.List.of()));
    }
}
