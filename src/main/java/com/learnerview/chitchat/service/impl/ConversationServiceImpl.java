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

    @Override
    public Conversation createDirectConversation(String currentUser, String otherUser) {
        String tenantId = TenantContext.getRequiredTenantId();
        if (currentUser.equals(otherUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create direct conversation with yourself");
        }
        if (!userRepository.existsByTenantIdAndUsername(tenantId, otherUser)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        return conversationRepository.findDirectConversation(tenantId, currentUser, otherUser).orElseGet(() -> {
            Set<String> participants = new HashSet<>();
            participants.add(currentUser);
            participants.add(otherUser);

            Conversation conversation = Conversation.builder()
                    .tenantId(tenantId)
                    .type(ConversationType.DM)
                    .createdBy(currentUser)
                    .createdAt(LocalDateTime.now())
                    .participants(participants)
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

        Set<String> participants = new HashSet<>();
        participants.add(currentUser);
        if (members != null) {
            Set<String> sanitizedMembers = members.stream()
                    .filter(m -> m != null && !m.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toSet());
                    
            for (String member : sanitizedMembers) {
                if (!member.matches("^[a-zA-Z0-9_]+$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username format: " + member);
                }
                if (!userRepository.existsByTenantIdAndUsername(tenantId, member)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + member);
                }
            }
            participants.addAll(sanitizedMembers);
        }

        Conversation conversation = Conversation.builder()
                .tenantId(tenantId)
                .type(ConversationType.GROUP)
                .name(name.trim())
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .participants(participants)
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
        return conversationRepository.findByTenantIdAndParticipantsContaining(TenantContext.getRequiredTenantId(), username);
    }

    @Override
    public Conversation getForUser(String conversationId, String username) {
        String tenantId = TenantContext.getRequiredTenantId();
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        if (!conversation.getParticipants().contains(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }

        return conversation;
    }

    @Override
    public Conversation renameConversation(String conversationId, String username, String newName) {
        Conversation conversation = getForUser(conversationId, username);
        assertGroupOwner(conversation, username);

        if (newName == null || newName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation name is required");
        }

        conversation.setName(newName.trim());
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation addParticipant(String conversationId, String requester, String participantUsername) {
        Conversation conversation = getForUser(conversationId, requester);
        assertGroupOwner(conversation, requester);

        if (!userRepository.existsByTenantIdAndUsername(TenantContext.getRequiredTenantId(), participantUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (conversation.getParticipants().contains(participantUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a participant");
        }

        conversation.getParticipants().add(participantUsername);
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation removeParticipant(String conversationId, String requester, String participantUsername) {
        Conversation conversation = getForUser(conversationId, requester);
        assertGroupOwner(conversation, requester);

        if (conversation.getCreatedBy().equals(participantUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be removed");
        }
        if (!conversation.getParticipants().contains(participantUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a participant");
        }

        conversation.getParticipants().remove(participantUsername);
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation transferOwnership(String conversationId, String username, String newOwner) {
        Conversation conversation = getForUser(conversationId, username);
        assertGroupOwner(conversation, username);

        if (newOwner == null || newOwner.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner is required");
        }
        newOwner = newOwner.trim();

        if (username.equals(newOwner)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner must be different from current owner");
        }

        if (!conversation.getParticipants().contains(newOwner)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New owner must be a current participant");
        }
        
        conversation.setCreatedBy(newOwner);
        return conversationRepository.save(conversation);
    }

    @Override
    public void leaveConversation(String conversationId, String username) {
        Conversation conversation = getForUser(conversationId, username);
        if (conversation.getType() == ConversationType.GROUP && conversation.getCreatedBy().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot leave. Transfer ownership first");
        }

        conversation.getParticipants().remove(username);
        conversationRepository.save(conversation);
    }

    private void assertGroupOwner(Conversation conversation, String username) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation is only valid for group conversations");
        }
        if (!username.equals(conversation.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only conversation owner can perform this action");
        }
    }
}
