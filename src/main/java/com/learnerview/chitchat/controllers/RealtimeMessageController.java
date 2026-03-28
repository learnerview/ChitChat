package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Message;
import com.learnerview.chitchat.service.MessageService;
import com.learnerview.chitchat.tenant.TenantContext;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class RealtimeMessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeMessageController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/conversations/{conversationId}/send")
    public void sendMessage(@DestinationVariable String conversationId,
                            @Payload RealtimeMessageRequest request,
                            SimpMessageHeaderAccessor headerAccessor,
                            Authentication authentication) {
        String tenantId = getTenantIdFromSession(headerAccessor);
        if (authentication == null || tenantId == null || tenantId.isBlank()) {
            return;
        }

        try {
            TenantContext.setTenantId(tenantId);

            Message saved = messageService.sendMessage(
                    conversationId,
                    authentication.getName(),
                    request.getContent(),
                    request.getReplyToId()
            );

            messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, saved);
        } finally {
            TenantContext.clear();
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

    @Data
    public static class RealtimeMessageRequest {
        @NotBlank
        private String content;
        private String replyToId;
    }
}
