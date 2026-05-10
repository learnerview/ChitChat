package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.TenantMember;
import com.learnerview.chitchat.repositories.TenantMemberRepository;
import com.learnerview.chitchat.service.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final TenantMemberRepository tenantMemberRepository;

    public MembershipServiceImpl(TenantMemberRepository tenantMemberRepository) {
        this.tenantMemberRepository = tenantMemberRepository;
    }

    @Override
    public TenantMember addMember(String tenantId, String userId, String role) {
        if (tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a member of this workspace");
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
        tenantMemberRepository.deleteByTenantIdAndUserId(tenantId, userId);
    }

    @Override
    public Optional<TenantMember> getMembership(String tenantId, String userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    @Override
    public List<TenantMember> getTenantMembers(String tenantId) {
        return tenantMemberRepository.findByTenantId(tenantId);
    }

    @Override
    public List<TenantMember> getUserMemberships(String userId) {
        return tenantMemberRepository.findByUserId(userId);
    }

    @Override
    public TenantMember updateMemberRole(String tenantId, String userId, String newRole) {
        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        member.setRole(newRole);
        return tenantMemberRepository.save(member);
    }

    @Override
    public boolean isMember(String tenantId, String userId) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId).isPresent();
    }

    @Override
    public boolean hasRole(String tenantId, String userId, String role) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(m -> role.equals(m.getRole()))
                .orElse(false);
    }
}
