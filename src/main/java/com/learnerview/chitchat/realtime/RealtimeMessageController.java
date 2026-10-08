package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.message.MessageService;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.ReadStateResponse;
import com.learnerview.chitchat.realtime.dto.ResumeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class RealtimeMessageController {

    private final MessageService messageService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final int syncBatchLimit;

    public RealtimeMessageController(MessageService messageService,
                                     RealtimeEventPublisher realtimeEventPublisher,
                                     @Value("${app.realtime.sync-batch-limit:200}") int syncBatchLimit) {
        this.messageService = messageService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.syncBatchLimit = syncBatchLimit;
    }

    /**
     * Reconnect/resume protocol. The client subscribes to the conversation
     * topic first, then sends its last processed sequence here; the server
     * replays the missing messages to the caller's private
     * {@code /user/queue/sync} (deduplicated by sequence on the client side
     * against anything already arriving on the topic), followed by a
     * SYNC_COMPLETE marker carrying the conversation's latest sequence.
     */
    @MessageMapping("/conversations/{conversationId}/resume")
    public void resume(@DestinationVariable String conversationId,
                       @Payload(required = false) ResumeRequest request,
                       SimpMessageHeaderAccessor headerAccessor,
                       Principal principal) {
        if (principal == null) {
            return;
        }
        String userId = principal.getName();

        withSessionContext(principal, headerAccessor, tenantId -> {
            long afterSequence = request == null || request.afterSequence() == null
                    ? 0L
                    : Math.max(0L, request.afterSequence());

            int delivered = 0;
            boolean hasMore = false;
            if (afterSequence > 0) {
                MessagePageResponse page =
                        messageService.getMessages(conversationId, null, afterSequence, syncBatchLimit);
                for (MessageResponse message : page.messages()) {
                    realtimeEventPublisher.publishToUser(userId, RealtimeEvent.of(
                            RealtimeEventType.MESSAGE_CREATED, tenantId, conversationId,
                            message.sequence(), message));
                    delivered++;
                }
                hasMore = page.hasMore();
            }

            ReadStateResponse state = messageService.getReadState(conversationId);
            realtimeEventPublisher.publishToUser(userId, RealtimeEvent.of(
                    RealtimeEventType.SYNC_COMPLETE, tenantId, conversationId, null,
                    Map.of(
                            "afterSequence", afterSequence,
                            "delivered", delivered,
                            "latestSequence", state.latestSequence(),
                            "hasMore", hasMore)));
        });
    }

    private void withSessionContext(Principal principal,
                                    SimpMessageHeaderAccessor headerAccessor,
                                    java.util.function.Consumer<String> action) {
        if (principal == null) {
            return;
        }
        String tenantId = getTenantIdFromSession(headerAccessor);
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }

        try {
            // Populate the same security context the REST path uses so that
            // authorization and identity resolution behave identically.
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal.getName(), null, java.util.List.of()));
            TenantContext.setTenantId(tenantId);
            action.accept(tenantId);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String getTenantIdFromSession(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object value = sessionAttributes.get("tenantId");
        return value == null ? null : value.toString();
    }
}
