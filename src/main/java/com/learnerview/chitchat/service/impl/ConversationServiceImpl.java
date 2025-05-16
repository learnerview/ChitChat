package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.*;
import com.learnerview.chitchat.repositories.*;
import com.learnerview.chitchat.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ConversationMembershipRepository membershipRepository;

    @Override
    public Conversation createDM(String username1, String username2) {
        // Check if DM already exists
        Optional<Conversation> existing = conversationRepository.findDMByUsers(username1, username2);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Verify users exist
        if (!userRepository.existsByUsername(username1) || !userRepository.existsByUsername(username2)) {
            throw new RuntimeException("One or both users not found");
        }
        
        Conversation dm = Conversation.builder()
                .type(ConversationType.DM)
                .users(List.of(username1, username2))
                .memberCount(2)
                .createdAt(LocalDateTime.now())
                .build();
        
        dm = conversationRepository.save(dm);
        
        // Add memberships
        createMembership(dm.getId(), username1, MembershipRole.MEMBER);
        createMembership(dm.getId(), username2, MembershipRole.MEMBER);
        
        return dm;
    }

    @Override
    public Conversation createGroup(String name, String ownerUsername, boolean isPublic, String description, String handle) {
        if (!userRepository.existsByUsername(ownerUsername)) {
            throw new RuntimeException("Owner not found");
        }
        
        if (isPublic && handle != null && conversationRepository.existsByHandle(handle)) {
            throw new RuntimeException("Handle already exists");
        }
        
        ConversationType type = isPublic ? ConversationType.PUBLIC_GROUP : ConversationType.PRIVATE_GROUP;
        
        Conversation group = Conversation.builder()
                .type(type)
                .name(name)
                .handle(isPublic ? handle : null)
                .description(description)
                .ownerId(ownerUsername)
                .memberCount(1)
                .createdAt(LocalDateTime.now())
                .inviteLink(isPublic ? null : UUID.randomUUID().toString())
                .build();
        
        group = conversationRepository.save(group);
        
        // Add owner as admin
        createMembership(group.getId(), ownerUsername, MembershipRole.OWNER);
        
        return group;
    }

    @Override
    public List<Conversation> getMyConversations(String username) {
        List<ConversationMembership> memberships = membershipRepository.findByUsernameAndStatus(username, MembershipStatus.APPROVED);
        return memberships.stream()
                .map(m -> conversationRepository.findById(m.getConversationId()).orElse(null))
                .filter(c -> c != null)
                .toList();
    }

    @Override
    public Conversation getConversation(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    @Override
    public List<Conversation> getPublicConversations(String username) {
        return conversationRepository.findByType(ConversationType.PUBLIC_GROUP);
    }

    @Override
    public Conversation joinPublicConversation(String conversationId, String username) {
        Conversation conversation = getConversation(conversationId);
        
        if (conversation.getType() != ConversationType.PUBLIC_GROUP) {
            throw new RuntimeException("Only public groups can be joined directly");
        }
        
        Optional<ConversationMembership> existing = membershipRepository
                .findByConversationIdAndUsername(conversationId, username);
        
        if (existing.isPresent()) {
            if (existing.get().getStatus() == MembershipStatus.APPROVED) {
                return conversation;
            }
            throw new RuntimeException("Already requested to join this conversation");
        }
        
        createMembership(conversationId, username, MembershipRole.MEMBER);
        
        conversation.setMemberCount(conversation.getMemberCount() + 1);
        return conversationRepository.save(conversation);
    }

    @Override
    public void deleteConversation(String conversationId, String username) {
        Conversation conversation = getConversation(conversationId);
        
        if (!conversation.getOwnerId().equals(username)) {
            throw new RuntimeException("Only owner can delete conversation");
        }
        
        conversationRepository.delete(conversation);
        membershipRepository.deleteByConversationIdAndUsername(conversationId, username);
    }

    @Override
    public void leaveConversation(String conversationId, String username) {
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        if (membership.getRole() == MembershipRole.OWNER) {
            throw new RuntimeException("Owner cannot leave. Transfer ownership first");
        }
        
        membership.setStatus(MembershipStatus.LEFT);
        membershipRepository.save(membership);
        
        Conversation conversation = getConversation(conversationId);
        conversation.setMemberCount(Math.max(0, conversation.getMemberCount() - 1));
        conversationRepository.save(conversation);
    }

    @Override
    public Conversation updateSettings(String conversationId, boolean adminOnlyMessaging, String username) {
        Conversation conversation = getConversation(conversationId);
        
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.ADMIN) {
            throw new RuntimeException("Only admins can update settings");
        }
        
        conversation.setAdminOnlyMessaging(adminOnlyMessaging);
        return conversationRepository.save(conversation);
    }

    @Override
    public Conversation updateGroup(String conversationId, String name, String description, String username) {
        Conversation conversation = getConversation(conversationId);
        
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.ADMIN) {
            throw new RuntimeException("Only admins can update group");
        }
        
        if (name != null) conversation.setName(name);
        if (description != null) conversation.setDescription(description);
        
        return conversationRepository.save(conversation);
    }

    @Override
    public String generateInviteLink(String conversationId, String username) {
        Conversation conversation = getConversation(conversationId);
        
        if (conversation.getType() == ConversationType.PUBLIC_GROUP) {
            throw new RuntimeException("Public groups don't need invite links");
        }
        
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.ADMIN) {
            throw new RuntimeException("Only admins can generate invite links");
        }
        
        String inviteLink = UUID.randomUUID().toString();
        conversation.setInviteLink(inviteLink);
        conversationRepository.save(conversation);
        
        return inviteLink;
    }

    @Override
    public void revokeInviteLink(String conversationId, String username) {
        Conversation conversation = getConversation(conversationId);
        
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.ADMIN) {
            throw new RuntimeException("Only admins can revoke invite links");
        }
        
        conversation.setInviteLink(null);
        conversationRepository.save(conversation);
    }

    @Override
    public Conversation joinViaInviteLink(String inviteCode, String username) {
        Conversation conversation = conversationRepository.findAll().stream()
                .filter(c -> inviteCode.equals(c.getInviteLink()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid invite link"));
        
        if (conversation.getType() != ConversationType.PRIVATE_GROUP) {
            throw new RuntimeException("Invite links only work for private groups");
        }
        
        Optional<ConversationMembership> existing = membershipRepository
                .findByConversationIdAndUsername(conversation.getId(), username);
        
        if (existing.isPresent()) {
            throw new RuntimeException("Already a member of this conversation");
        }
        
        createMembership(conversation.getId(), username, MembershipRole.MEMBER);
        
        conversation.setMemberCount(conversation.getMemberCount() + 1);
        return conversationRepository.save(conversation);
    }

    @Override
    public void togglePin(String conversationId, String username) {
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        membership.setPinned(!membership.isPinned());
        membershipRepository.save(membership);
    }

    @Override
    public void toggleMute(String conversationId, String username) {
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("Not a member of this conversation"));
        
        membership.setMuted(!membership.isMuted());
        membershipRepository.save(membership);
    }
    
    private void createMembership(String conversationId, String username, MembershipRole role) {
        ConversationMembership membership = new ConversationMembership(conversationId, username);
        membership.setRole(role);
        membershipRepository.save(membership);
    }
}
