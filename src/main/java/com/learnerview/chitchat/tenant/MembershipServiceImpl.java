package com.learnerview.chitchat.tenant;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final TenantMemberRepository tenantMemberRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    public MembershipServiceImpl(TenantMemberRepository tenantMemberRepository,
                                 ConversationMemberRepository conversationMemberRepository) {
        this.tenantMemberRepository = tenantMemberRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @Override
    public TenantMember addMember(String tenantId, String userId, String role) {
        TenantMember existing = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElse(null);

        if (existing != null && existing.isActive()) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER);
        }

        if (existing != null) {
            // Reinstatement: the historical membership row is revived.
            existing.setRemovedAt(null);
            existing.setRole(role);
            existing.setJoinedAt(LocalDateTime.now());
            return tenantMemberRepository.save(existing);
        }

        TenantMember member = TenantMember.builder()
                .tenantId(tenantId)
                .userId(userId)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();

        return tenantMemberRepository.save(member);
    }

    @Override
    public void removeMember(String tenantId, String userId) {
        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(TenantMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Membership not found"));

        if (TenantMember.Role.OWNER.name().equals(member.getRole())
                && tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(
                        tenantId, TenantMember.Role.OWNER.name()) <= 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Cannot remove the last workspace owner. Transfer ownership first");
        }

        // Soft delete keeps the audit trail; reinstatement revives this row.
        member.setRemovedAt(LocalDateTime.now());
        tenantMemberRepository.save(member);

        // Cascade: the user immediately loses every conversation membership in
        // this workspace, so REST and realtime both stop serving them.
        conversationMemberRepository
                .findByTenantIdAndUserIdAndLeftAtIsNull(tenantId, userId)
                .forEach(conversationMember -> {
                    conversationMember.setLeftAt(LocalDateTime.now());
                    conversationMemberRepository.save(conversationMember);
                });
    }

    @Override
    public Optional<TenantMember> getMembership(String tenantId, String userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(TenantMember::isActive);
    }

    @Override
    public List<TenantMember> getTenantMembers(String tenantId) {
        return tenantMemberRepository.findByTenantIdAndRemovedAtIsNull(tenantId);
    }

    @Override
    public List<TenantMember> getUserMemberships(String userId) {
        return tenantMemberRepository.findByUserIdAndRemovedAtIsNull(userId);
    }

    @Override
    public boolean isMember(String tenantId, String userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(TenantMember::isActive)
                .isPresent();
    }

    @Override
    public boolean hasRole(String tenantId, String userId, String role) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(TenantMember::isActive)
                .map(m -> role.equals(m.getRole()))
                .orElse(false);
    }
}
