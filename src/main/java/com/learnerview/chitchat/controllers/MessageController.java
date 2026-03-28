package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Message;
import com.learnerview.chitchat.service.MessageService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{conversationId}")
    public List<Message> getMessageHistory(
            @PathVariable String conversationId,
            org.springframework.security.core.Authentication auth
    ) {
        return messageService.getMessageHistory(conversationId, auth.getName());
    }

    @PostMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Message sendMessage(
            @PathVariable String conversationId,
            @RequestBody SendMessageRequest request,
            org.springframework.security.core.Authentication auth
    ) {
        return messageService.sendMessage(conversationId, auth.getName(), request.getContent(), request.getReplyToId());
    }

    @PostMapping("/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @PathVariable String conversationId,
            org.springframework.security.core.Authentication auth
    ) {
        messageService.markAsRead(conversationId, auth.getName());
    }

    @GetMapping("/{conversationId}/paginated")
    public Map<String, Object> getMessageHistoryPaginated(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            org.springframework.security.core.Authentication auth
    ) {
        return messageService.getMessageHistoryPaginated(conversationId, auth.getName(), page, size);
    }

    @PatchMapping("/{messageId}")
    public Message editMessage(@PathVariable String messageId,
                               @RequestBody EditMessageRequest request,
                               org.springframework.security.core.Authentication auth) {
        return messageService.editMessage(messageId, auth.getName(), request.getContent());
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String messageId,
                              org.springframework.security.core.Authentication auth) {
        messageService.deleteMessage(messageId, auth.getName());
    }

    @Data
    public static class SendMessageRequest {
        @NotBlank
        private String content;
        private String replyToId;
    }

    @Data
    @NoArgsConstructor
    public static class EditMessageRequest {
        @NotBlank
        private String content;
    }
}