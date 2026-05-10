package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.entities.Message;
import com.learnerview.chitchat.repositories.ConversationRepository;
import com.learnerview.chitchat.repositories.MessageRepository;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.service.EventPublisherService;
import com.learnerview.chitchat.service.MessageService;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final EventPublisherService eventPublisherService;

    public MessageServiceImpl(MessageRepository messageRepository,
                              ConversationRepository conversationRepository,
                              UserRepository userRepository,
                              EventPublisherService eventPublisherService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.eventPublisherService = eventPublisherService;
    }

    private String getUserIdFromUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username))
                .getId();
    }

    @Override
    public Message sendMessage(String conversationId, String sender, String content) {
        return sendMessage(conversationId, sender, content, null);
    }

    @Override
    public Message sendMessage(String conversationId, String sender, String content, String replyToId) {
        String tenantId = TenantContext.getRequiredTenantId();
        String senderId = getUserIdFromUsername(sender);
        
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getParticipantIds().contains(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        if (replyToId != null && !replyToId.isBlank()) {
            Message parent = messageRepository.findByIdAndTenantId(replyToId, tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target message not found"));
            if (!conversationId.equals(parent.getConversationId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target belongs to a different conversation");
            }
        }

        Message message = Message.builder()
            .tenantId(tenantId)
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content.trim())
                .replyToId(replyToId)
                .createdAt(LocalDateTime.now())
                .build();

        Message saved = messageRepository.save(message);
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("messageId", saved.getId());
        eventPayload.put("conversationId", saved.getConversationId());
        eventPayload.put("senderId", saved.getSenderId());
        if (saved.getReplyToId() != null) {
            eventPayload.put("replyToId", saved.getReplyToId());
        }
        eventPublisherService.publish(tenantId, "message.sent", eventPayload);
        return saved;
    }

    @Override
    public List<Message> getMessageHistory(String conversationId, String viewerUsername) {
        String tenantId = TenantContext.getRequiredTenantId();
        String viewerId = getUserIdFromUsername(viewerUsername);
        
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getParticipantIds().contains(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        return messageRepository.findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversationId);
    }

    @Override
    public Map<String, Object> getMessageHistoryPaginated(String conversationId, String viewerUsername, int page, int size) {
        String tenantId = TenantContext.getRequiredTenantId();
        String viewerId = getUserIdFromUsername(viewerUsername);
        
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getParticipantIds().contains(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Message> messagePage = messageRepository.findByTenantIdAndConversationId(tenantId, conversationId, pageable);
        return Map.of(
                "content", messagePage.getContent(),
                "page", messagePage.getNumber(),
                "size", messagePage.getSize(),
                "totalElements", messagePage.getTotalElements(),
                "totalPages", messagePage.getTotalPages(),
                "last", messagePage.isLast()
        );
    }

    @Override
    public void markAsRead(String conversationId, String username) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = getUserIdFromUsername(username);
        getAuthorizedConversation(conversationId, username);

        List<Message> unreadMessages = messageRepository.findByTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversationId)
            .stream()
            .filter(m -> !m.getReadBy().contains(userId) && !m.getSenderId().equals(userId))
            .toList();

        if (!unreadMessages.isEmpty()) {
            for (Message m : unreadMessages) {
                m.getReadBy().add(userId);
            }
            messageRepository.saveAll(unreadMessages);
        }
    }

    @Override
    public Message editMessage(String messageId, String editor, String updatedContent) {
        String tenantId = TenantContext.getRequiredTenantId();
        String editorId = getUserIdFromUsername(editor);
        
        Message message = messageRepository.findByIdAndTenantId(messageId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        Conversation conversation = getAuthorizedConversation(message.getConversationId(), editor);
        if (!conversation.getParticipantIds().contains(editorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }
        if (!message.getSenderId().equals(editorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sender can edit this message");
        }
        if (updatedContent == null || updatedContent.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted message cannot be edited");
        }

        message.setContent(updatedContent.trim());
        message.setEdited(true);
        message.setUpdatedAt(LocalDateTime.now());
        return messageRepository.save(message);
    }

    @Override
    public void deleteMessage(String messageId, String requester) {
        String tenantId = TenantContext.getRequiredTenantId();
        String requesterId = getUserIdFromUsername(requester);
        
        Message message = messageRepository.findByIdAndTenantId(messageId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        Conversation conversation = getAuthorizedConversation(message.getConversationId(), requester);
        boolean isOwner = requesterId.equals(conversation.getCreatedBy());
        boolean isSender = requesterId.equals(message.getSenderId());

        if (!isOwner && !isSender) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sender or conversation owner can delete message");
        }
        if (message.isDeleted()) {
            return;
        }

        message.setDeleted(true);
        message.setContent("This message was deleted");
        message.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(message);

        eventPublisherService.publish(tenantId, "message.deleted", Map.of(
            "messageId", message.getId(),
            "conversationId", message.getConversationId(),
            "requester", requester
        ));
    }

    @Override
    public List<Message> searchMessages(String conversationId, String username, String query) {
        String tenantId = TenantContext.getRequiredTenantId();
        getAuthorizedConversation(conversationId, username); // Verifies membership

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return messageRepository.findByTenantIdAndConversationIdAndContentContainingIgnoreCase(tenantId, conversationId, query.trim());
    }

    @Override
    public List<Message> searchAllMyMessages(String username, String query) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = getUserIdFromUsername(username);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Find all conversations the user is a part of
        List<String> conversationIds = conversationRepository.findByTenantIdAndParticipantIdsContaining(tenantId, userId)
                .stream()
                .map(Conversation::getId)
                .toList();

        if (conversationIds.isEmpty()) {
            return List.of();
        }

        return messageRepository.findByTenantIdAndConversationIdInAndContentContainingIgnoreCase(tenantId, conversationIds, query.trim());
    }

    private Conversation getAuthorizedConversation(String conversationId, String username) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = getUserIdFromUsername(username);
        
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getParticipantIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }
        return conversation;
    }
}
