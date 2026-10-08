package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InviteLinkServiceImpl implements InviteLinkService {

    private final InviteLinkRepository inviteLinkRepository;
    private final MembershipService membershipService;
    private final TenantRepository tenantRepository;

    public InviteLinkServiceImpl(InviteLinkRepository inviteLinkRepository,
                                 MembershipService membershipService,
                                 TenantRepository tenantRepository) {
        this.inviteLinkRepository = inviteLinkRepository;
        this.membershipService = membershipService;
        this.tenantRepository = tenantRepository;
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
    public String acceptInvite(String token, String userId) {
        InviteLink invite = inviteLinkRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Invite link not found"));

        if (invite.isExpired()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invite link has expired");
        }

        tenantRepository.findById(invite.getTenantId())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_ACCESS_DENIED,
                        "This workspace is no longer active"));

        membershipService.addMember(invite.getTenantId(), userId, TenantMember.Role.MEMBER.name());
        inviteLinkRepository.delete(invite);
        return invite.getTenantId();
    }

    @Override
    public void revokeInvite(String token, String tenantId) {
        // The token must belong to the tenant the caller was authorized for -
        // otherwise any workspace owner could delete another workspace's invites.
        InviteLink invite = inviteLinkRepository.findByToken(token)
                .filter(found -> found.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Invite link not found"));

        inviteLinkRepository.delete(invite);
    }

    @Override
    public List<InviteLink> getTenantInvites(String tenantId) {
        return inviteLinkRepository.findByTenantId(tenantId).stream()
                .filter(invite -> !invite.isExpired())
                .toList();
    }
}
