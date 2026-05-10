package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.InviteLink;
import com.learnerview.chitchat.repositories.InviteLinkRepository;
import com.learnerview.chitchat.service.InviteLinkService;
import com.learnerview.chitchat.service.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InviteLinkServiceImpl implements InviteLinkService {

    private final InviteLinkRepository inviteLinkRepository;
    private final MembershipService membershipService;

    public InviteLinkServiceImpl(InviteLinkRepository inviteLinkRepository, MembershipService membershipService) {
        this.inviteLinkRepository = inviteLinkRepository;
        this.membershipService = membershipService;
    }

    @Override
    public InviteLink createInviteLink(String tenantId, String createdBy, LocalDateTime expiresAt) {
        InviteLink invite = InviteLink.builder()
                .tenantId(tenantId)
                .createdBy(createdBy)
                .token(UUID.randomUUID().toString())
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();

        return inviteLinkRepository.save(invite);
    }

    @Override
    public Optional<InviteLink> getByToken(String token) {
        return inviteLinkRepository.findByToken(token);
    }

    @Override
    public void acceptInvite(String token, String userId) {
        InviteLink invite = inviteLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found"));

        if (invite.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite link has expired");
        }

        // Add user to tenant as MEMBER
        membershipService.addMember(invite.getTenantId(), userId, "MEMBER");
        
        // Mark invite as used (soft delete by removing token)
        inviteLinkRepository.delete(invite);
    }

    @Override
    public void revokeInvite(String token) {
        InviteLink invite = inviteLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found"));

        inviteLinkRepository.delete(invite);
    }

    @Override
    public boolean isValidInvite(String token) {
        return inviteLinkRepository.findByToken(token)
                .map(invite -> !invite.isExpired())
                .orElse(false);
    }

    @Override
    public List<InviteLink> getTenantInvites(String tenantId) {
        return inviteLinkRepository.findByTenantId(tenantId).stream()
                .filter(invite -> !invite.isExpired())
                .toList();
    }
}
