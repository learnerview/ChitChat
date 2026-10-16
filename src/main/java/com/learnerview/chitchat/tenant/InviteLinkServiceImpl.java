package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InviteLinkServiceImpl implements InviteLinkService {

    private final InviteLinkRepository inviteLinkRepository;
    private final MembershipService membershipService;
    private final TenantRepository tenantRepository;
    private final MongoTemplate mongoTemplate;

    public InviteLinkServiceImpl(InviteLinkRepository inviteLinkRepository,
                                 MembershipService membershipService,
                                 TenantRepository tenantRepository,
                                 MongoTemplate mongoTemplate) {
        this.inviteLinkRepository = inviteLinkRepository;
        this.membershipService = membershipService;
        this.tenantRepository = tenantRepository;
        this.mongoTemplate = mongoTemplate;
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

        UpdateResult claim = mongoTemplate.updateFirst(
                Query.query(Criteria.where("token").is(token).and("used").ne(true)),
                new Update().set("used", true).set("usedAt", LocalDateTime.now()),
                InviteLink.class);
        if (claim.getModifiedCount() == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Invite link not found");
        }

        try {
            membershipService.addMember(invite.getTenantId(), userId, TenantMember.Role.MEMBER.name());
        } catch (RuntimeException ex) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("token").is(token).and("used").is(true)),
                    new Update().set("used", false).unset("usedAt"),
                    InviteLink.class);
            throw ex;
        }

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
