package com.learnerview.chitchat.authorization;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.conversation.ConversationRepository;
import com.learnerview.chitchat.conversation.ConversationType;
import com.learnerview.chitchat.tenant.Tenant;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import com.learnerview.chitchat.tenant.TenantRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Single place where identity, tenant membership and conversation membership
 * are resolved. Controllers and services never hand-roll authorization logic.
 */
@Service
public class AuthorizationService {

    private final TenantMemberRepository tenantMemberRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final TenantRepository tenantRepository;

    public AuthorizationService(TenantMemberRepository tenantMemberRepository,
                                ConversationRepository conversationRepository,
                                ConversationMemberRepository conversationMemberRepository,
                                TenantRepository tenantRepository) {
        this.tenantMemberRepository = tenantMemberRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.tenantRepository = tenantRepository;
    }

    /** The authenticated userId (JWT subject). Never a username. */
    public String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    public TenantMember requireTenantMember(String tenantId) {
        String userId = currentUserId();
        // A deleted (soft-deactivated) workspace denies everyone, members included.
        tenantRepository.findById(tenantId)
                .filter(tenant -> !tenant.isActive())
                .ifPresent(tenant -> {
                    throw new ApiException(ErrorCode.TENANT_ACCESS_DENIED,
                            "This workspace has been deleted");
                });
        return tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(TenantMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_ACCESS_DENIED));
    }

    public TenantMember requireTenantRole(String tenantId, TenantMember.Role... allowedRoles) {
        TenantMember membership = requireTenantMember(tenantId);
        boolean permitted = Arrays.stream(allowedRoles).anyMatch(role -> role.name().equals(membership.getRole()));
        if (!permitted) {
            throw new ApiException(ErrorCode.INSUFFICIENT_ROLE);
        }
        return membership;
    }

    public record ConversationAccess(Conversation conversation, ConversationMember member, String userId) {
    }

    /** Verifies tenant membership plus active conversation membership. */
    public ConversationAccess requireConversationAccess(String conversationId) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = currentUserId();

        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));

        requireTenantMember(tenantId);

        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .filter(ConversationMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_ACCESS_DENIED));

        return new ConversationAccess(conversation, member, userId);
    }

    /** Conversation-level authority (group owner/admin operations). */
    public ConversationAccess requireConversationRole(String conversationId, ConversationMember.Role... allowedRoles) {
        ConversationAccess access = requireConversationAccess(conversationId);
        if (access.conversation().getType() == ConversationType.DM) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Operation is only valid for group conversations");
        }
        boolean permitted = Arrays.stream(allowedRoles)
                .anyMatch(role -> role == access.member().getRole());
        if (!permitted) {
            throw new ApiException(ErrorCode.INSUFFICIENT_CONVERSATION_ROLE);
        }
        return access;
    }
}
