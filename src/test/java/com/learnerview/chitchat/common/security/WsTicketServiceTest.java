package com.learnerview.chitchat.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WsTicketServiceTest {

    private final WsTicketService ticketService = new WsTicketService(60);

    @Test
    void issuedTicketResolvesUserAndTenantOnce() {
        String ticket = ticketService.issue("user-1", "tenant-1");

        var consumed = ticketService.consume(ticket);

        assertThat(consumed).isPresent();
        assertThat(consumed.get().userId()).isEqualTo("user-1");
        assertThat(consumed.get().tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void ticketsAreSingleUse() {
        String ticket = ticketService.issue("user-1", "tenant-1");
        ticketService.consume(ticket);

        assertThat(ticketService.consume(ticket)).isEmpty();
    }

    @Test
    void unknownAndBlankTicketsAreRejected() {
        assertThat(ticketService.consume("nope")).isEmpty();
        assertThat(ticketService.consume("")).isEmpty();
        assertThat(ticketService.consume(null)).isEmpty();
    }

    @Test
    void expiredTicketsAreRejected() {
        WsTicketService shortLived = new WsTicketService(-1);
        String ticket = shortLived.issue("user-1", "tenant-1");

        assertThat(shortLived.consume(ticket)).isEmpty();
    }
}
