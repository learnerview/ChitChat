package com.learnerview.chitchat.message;

import com.learnerview.chitchat.message.dto.EditMessageRequest;
import com.learnerview.chitchat.message.dto.MarkReadRequest;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(@PathVariable String conversationId,
                                       @Valid @RequestBody SendMessageRequest request) {
        return messageService.send(conversationId, request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public MessagePageResponse getMessages(@PathVariable String conversationId,
                                           @RequestParam(required = false) Long before,
                                           @RequestParam(required = false) Long after,
                                           @RequestParam(required = false) Integer limit) {
        return messageService.getMessages(conversationId, before, after, limit);
    }

    @GetMapping("/conversations/{conversationId}/messages/search")
    public List<MessageResponse> searchInConversation(@PathVariable String conversationId,
                                                      @RequestParam String query) {
        return messageService.searchInConversation(conversationId, query);
    }

    @PostMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable String conversationId,
                           @RequestBody(required = false) MarkReadRequest request) {
        messageService.markAsRead(conversationId, request == null ? new MarkReadRequest(null) : request);
    }

    @PatchMapping("/messages/{messageId}")
    public MessageResponse editMessage(@PathVariable String messageId,
                                       @Valid @RequestBody EditMessageRequest request) {
        return messageService.editMessage(messageId, request);
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String messageId) {
        messageService.deleteMessage(messageId);
    }

    @GetMapping("/messages/search")
    public List<MessageResponse> searchAll(@RequestParam String query) {
        return messageService.searchAll(query);
    }
}
