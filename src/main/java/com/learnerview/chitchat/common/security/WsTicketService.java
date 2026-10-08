package com.learnerview.chitchat.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tickets for WebSocket authentication. Browsers
 * cannot set Authorization headers on SockJS handshakes; a ticket lets the
 * client authenticate the upgrade without placing the long-lived JWT in the
 * URL (which would leak into access/proxy logs).
 *
 * In-memory by design: tickets survive seconds, not restarts, and clients
 * simply fetch a new one. A shared store (e.g. Redis) is only needed for
 * multi-instance deployments.
 */
@Service
public class WsTicketService {

    public record WsTicket(String userId, String tenantId) {
    }

    private record Entry(String userId, String tenantId, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> tickets = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public WsTicketService(@Value("${app.ws.ticket-ttl-seconds:60}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String userId, String tenantId) {
        evictExpired();
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new Entry(userId, tenantId, Instant.now().plusSeconds(ttlSeconds)));
        return ticket;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** Single-use: the first successful consume invalidates the ticket. */
    public Optional<WsTicket> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(new WsTicket(entry.userId(), entry.tenantId()));
    }

    private void evictExpired() {
        Instant now = Instant.now();
        tickets.values().removeIf(entry -> entry.expiresAt().isBefore(now));
    }
}
