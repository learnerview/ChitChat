package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.entities.ConversationType;
import com.learnerview.chitchat.repositories.ConversationRepository;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.service.ConversationService;
import com.learnerview.chitchat.service.EventPublisherService;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final EventPublisherService eventPublisherService;

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   UserRepository userRepository,
                                   EventPublisherService eventPublisherService) {
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
    public Conversation createDirectConversation(String currentUser, String otherUser) {
        String tenantId = TenantContext.getRequiredTenantId();
        if (currentUser.equals(otherUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create direct conversation with yourself");
        }
        
        String currentUserId = getUserIdFromUsername(currentUser);
        String otherUserId = getUserIdFromUsername(otherUser);

        return conversationRepository.findDirectConversation(tenantId, currentUserId, otherUserId).orElseGet(() -> {
            Set<String> participantIds = new HashSet<>();
            participantIds.add(currentUserId);
            participantIds.add(otherUserId);

            Conversation conversation = Conversation.builder()
                    .tenantId(tenantId)
                    .type(ConversationType.DM)
                    .createdBy(currentUserId)
                    .createdAt(LocalDateTime.now())
                    .participantIds(participantIds)
                    .build();

            Conversation saved = conversationRepository.save(conversation);
            eventPublisherService.publish(tenantId, "conversation.created", Map.of(
                    "conversationId", saved.getId(),
                    "type", saved.getType().name(),
                    "createdBy", saved.getCreatedBy()
            ));
            return saved;
        });
    }

    @Override
    public Conversation createGroupConversation(String currentUser, String name, Set<String> members) {
        String tenantId = TenantContext.getRequiredTenantId();
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group name is required");
        }

        String currentUserId = getUserIdFromUsername(currentUser);
        Set<String> participantIds = new HashSet<>();
        participantIds.add(currentUserId);
        
        if (members != null) {
            Set<String> sanitizedMembers = members.stream()
                    .filter(m -> m != null && !m.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toSet());
                    
            for (String member : sanitizedMembers) {
                if (!member.matches("^[a-zA-Z0-9_]+$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username format: " + member);
                }
                if (!userRepository.existsByUsername(member)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + member);
                }
                participantIds.add(getUserIdFromUsername(member));
            }
        }

        Conversation conversation = Conversation.builder()
                .tenantId(tenantId)
                .type(ConversationType.GROUP)
                .name(name.trim())
                .createdBy(currentUserId)
                .createdAt(LocalDateTime.now())
                .participantIds(participantIds)
                .build();

        Conversation saved = conversationRepository.save(conversation);
        eventPublisherService.publish(tenantId, "conversation.created", Map.of(
                "conversationId", saved.getId(),
                "type", saved.getType().name(),
                "createdBy", saved.getCreatedBy(),
                "name", saved.getName()
        ));
        return saved;
    }

    @Override
    public List<Conversation> listForUser(String username) {
        String userId = getUserIdFromUsername(username);
        return conversationRepository.findByTenantIdAndParticipantIdsContaining(TenantContext.getRequiredTenantId(), userId);
    }

    @Override
    public Conversation getForUser(String conversationId, String username) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = getUserIdFromUsername(username);
        
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getParticipantIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        return conversation;
    }

    @Override
    public Conversation renameConversation(String conversationId, String username, String newName) {
        Conversation conversation = getForUser(conversationId, username);
        String userId = getUserIdFromUsername(username);
        assertGroupOwner(conversation, userId);

        if (newName == null || newName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation name is required");
        }

        conversation.setName(newName.trim());
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation addParticipant(String conversationId, String requester, String participantUsername) {
        Conversation conversation = getForUser(conversationId, requester);
        String requesterId = getUserIdFromUsername(requester);
        assertGroupOwner(conversation, requesterId);

        String participantId = getUserIdFromUsername(participantUsername);
        
        if (conversation.getParticipantIds().contains(participantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a participant");
        }

        conversation.getParticipantIds().add(participantId);
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation removeParticipant(String conversationId, String requester, String participantUsername) {
        Conversation conversation = getForUser(conversationId, requester);
        String requesterId = getUserIdFromUsername(requester);
        assertGroupOwner(conversation, requesterId);

        String participantId = getUserIdFromUsername(participantUsername);
        
        if (conversation.getCreatedBy().equals(participantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be removed");
        }
        if (!conversation.getParticipantIds().contains(participantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a participant");
        }

        conversation.getParticipantIds().remove(participantId);
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation transferOwnership(String conversationId, String username, String newOwner) {
        Conversation conversation = getForUser(conversationId, username);
        String userId = getUserIdFromUsername(username);
        assertGroupOwner(conversation, userId);

        if (newOwner == null || newOwner.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner is required");
        }
        newOwner = newOwner.trim();

        String newOwnerId = getUserIdFromUsername(newOwner);
        
        if (userId.equals(newOwnerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner must be different from current owner");
        }

        if (!conversation.getParticipantIds().contains(newOwnerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner must be a current participant");
        }
        
        conversation.setCreatedBy(newOwnerId);
        return conversationRepository.save(conversation);
    }

    @Override
    public void leaveConversation(String conversationId, String username) {
        Conversation conversation = getForUser(conversationId, username);
        String userId = getUserIdFromUsername(username);
        
        if (conversation.getType() == ConversationType.GROUP && conversation.getCreatedBy().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot leave. Transfer ownership first");
        }

        conversation.getParticipantIds().remove(userId);
        conversationRepository.save(conversation);
    }

    private void assertGroupOwner(Conversation conversation, String userId) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation is only valid for group conversations");
        }
        if (!userId.equals(conversation.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only conversation owner can perform this action");
        }
    }
}
