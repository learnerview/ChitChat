package com.learnerview.chitchat.conversation;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.event.EventPublisherService;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.conversation.dto.ConversationMemberResponse;
import com.learnerview.chitchat.conversation.dto.ConversationResponse;
import com.learnerview.chitchat.realtime.RealtimeEvent;
import com.learnerview.chitchat.realtime.RealtimeEventPublisher;
import com.learnerview.chitchat.realtime.RealtimeEventType;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final AuthorizationService authorizationService;
    private final EventPublisherService eventPublisherService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Value("${app.limits.max-group-members:500}")
    private int maxGroupMembers;

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   ConversationMemberRepository memberRepository,
                                   UserRepository userRepository,
                                   TenantMemberRepository tenantMemberRepository,
                                   AuthorizationService authorizationService,
                                   EventPublisherService eventPublisherService,
                                   RealtimeEventPublisher realtimeEventPublisher,
                                   MongoTemplate mongoTemplate) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.authorizationService = authorizationService;
        this.eventPublisherService = eventPublisherService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ConversationResponse createDirectConversation(String otherUserId) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = authorizationService.currentUserId();
        authorizationService.requireTenantMember(tenantId);

        if (Objects.equals(userId, otherUserId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot create direct conversation with yourself");
        }
        userRepository.findById(otherUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        tenantMemberRepository.findByTenantIdAndUserId(tenantId, otherUserId)
                .filter(com.learnerview.chitchat.tenant.TenantMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_ACCESS_DENIED,
                        "User is not a member of this workspace"));

        String directKey = Conversation.directKey(userId, otherUserId);

        Conversation existing = conversationRepository.findByTenantIdAndDirectKey(tenantId, directKey)
                .orElse(null);
        if (existing != null) {
            return reactivateDirectConversation(existing, tenantId, userId, otherUserId);
        }

        // Members are written before the conversation (which stays invisible
        // until it exists); a failure is compensated by removing both sides.
        String conversationId = new ObjectId().toString();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .type(ConversationType.DM)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .directKey(directKey)
                .lastMessageSequence(0L)
                .status(ConversationStatus.ACTIVE)
                .build();

        try {
            memberRepository.saveAll(List.of(
                    newMember(tenantId, conversationId, userId, ConversationMember.Role.MEMBER),
                    newMember(tenantId, conversationId, otherUserId, ConversationMember.Role.MEMBER)));
            Conversation saved = conversationRepository.save(conversation);

            eventPublisherService.publish(tenantId, "conversation.created", Map.of(
                    "conversationId", saved.getId(),
                    "type", saved.getType().name(),
                    "createdBy", userId
            ));
            return detail(saved, userId);
        } catch (DuplicateKeyException ex) {
            // Two requests raced to create the same DM; the unique (tenantId, directKey)
            // index decided the winner - drop our orphaned member rows and reuse it.
            removeOrphanedMembers(conversationId);
            Conversation raced = conversationRepository.findByTenantIdAndDirectKey(tenantId, directKey)
                    .orElseThrow(() -> ex);
            return reactivateDirectConversation(raced, tenantId, userId, otherUserId);
        } catch (RuntimeException ex) {
            compensateFailedCreation(conversationId);
            throw ex;
        }
    }

    private ConversationResponse reactivateDirectConversation(Conversation conversation,
                                                              String tenantId,
                                                              String userId,
                                                              String otherUserId) {
        reviveMembership(conversation, tenantId, userId);
        reviveMembership(conversation, tenantId, otherUserId);
        return detail(conversation, userId);
    }

    private void reviveMembership(Conversation conversation, String tenantId, String userId) {
        ConversationMember member = memberRepository
                .findByConversationIdAndUserId(conversation.getId(), userId)
                .orElse(null);
        if (member == null) {
            memberRepository.save(newMember(tenantId, conversation.getId(), userId, ConversationMember.Role.MEMBER));
        } else if (!member.isActive()) {
            member.setLeftAt(null);
            member.setJoinedAt(LocalDateTime.now());
            memberRepository.save(member);
        }
    }

    @Override
    public ConversationResponse createGroupConversation(String name, Set<String> memberIds) {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = authorizationService.currentUserId();
        authorizationService.requireTenantMember(tenantId);

        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Group name is required");
        }

        Set<String> targetUserIds = new LinkedHashSet<>();
        if (memberIds != null) {
            memberIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .forEach(targetUserIds::add);
        }
        targetUserIds.remove(userId);

        if (targetUserIds.size() + 1 > maxGroupMembers) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Group conversations are limited to " + maxGroupMembers + " members");
        }

        // Batch validation: two queries total, not two per member.
        Set<String> existingUserIds = userRepository.findAllById(targetUserIds).stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (String targetUserId : targetUserIds) {
            if (!existingUserIds.contains(targetUserId)) {
                throw new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + targetUserId);
            }
        }
        Set<String> workspaceMemberIds = tenantMemberRepository
                .findByTenantIdAndUserIdIn(tenantId, targetUserIds).stream()
                .filter(com.learnerview.chitchat.tenant.TenantMember::isActive)
                .map(com.learnerview.chitchat.tenant.TenantMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        for (String targetUserId : targetUserIds) {
            if (!workspaceMemberIds.contains(targetUserId)) {
                throw new ApiException(ErrorCode.TENANT_ACCESS_DENIED,
                        "User is not a member of this workspace: " + targetUserId);
            }
        }

        // Members first (invisible without the conversation), conversation last;
        // any failure is compensated so a retry never sees a half-created group.
        String conversationId = new ObjectId().toString();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .type(ConversationType.GROUP)
                .name(name.trim())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastMessageSequence(0L)
                .status(ConversationStatus.ACTIVE)
                .build();

        List<ConversationMember> members = new ArrayList<>();
        members.add(newMember(tenantId, conversationId, userId, ConversationMember.Role.OWNER));
        for (String targetUserId : targetUserIds) {
            members.add(newMember(tenantId, conversationId, targetUserId, ConversationMember.Role.MEMBER));
        }

        try {
            memberRepository.saveAll(members);
            Conversation saved = conversationRepository.save(conversation);

            eventPublisherService.publish(tenantId, "conversation.created", Map.of(
                    "conversationId", saved.getId(),
                    "type", saved.getType().name(),
                    "createdBy", userId,
                    "name", saved.getName()
            ));
            return detail(saved, userId);
        } catch (RuntimeException ex) {
            compensateFailedCreation(conversationId);
            throw ex;
        }
    }

    @Override
    public List<ConversationResponse> listForUser() {
        String tenantId = TenantContext.getRequiredTenantId();
        String userId = authorizationService.currentUserId();
        authorizationService.requireTenantMember(tenantId);

        List<ConversationMember> memberships =
                memberRepository.findByTenantIdAndUserIdAndLeftAtIsNull(tenantId, userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<String> conversationIds = memberships.stream()
                .map(ConversationMember::getConversationId)
                .toList();
        Map<String, ConversationMember> membershipByConversation = memberships.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConversationMember::getConversationId, m -> m, (a, b) -> a));

        return conversationRepository.findByTenantIdAndIdIn(tenantId, conversationIds).stream()
                .sorted(Comparator.comparing(
                        (Conversation c) -> c.getLastMessageAt() != null ? c.getLastMessageAt() : c.getCreatedAt())
                        .reversed())
                .map(c -> ConversationResponse.summary(c, membershipByConversation.get(c.getId())))
                .toList();
    }

    @Override
    public ConversationResponse getForUser(String conversationId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        return detail(access.conversation(), access.userId());
    }

    @Override
    public ConversationResponse renameConversation(String conversationId, String newName) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationRole(conversationId, ConversationMember.Role.OWNER);

        if (newName == null || newName.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Conversation name is required");
        }

        // Targeted update only - a full-document save would rewind
        // lastMessageSequence/lastMessageAt written by concurrent sends.
        Conversation conversation = access.conversation();
        String trimmed = newName.trim();
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(conversation.getId())),
                new Update().set("name", trimmed).set("updatedAt", LocalDateTime.now()),
                Conversation.class);
        conversation.setName(trimmed);
        return detail(conversation, access.userId());
    }

    @Override
    public ConversationResponse addMember(String conversationId, String targetUserId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationRole(conversationId, ConversationMember.Role.OWNER);
        String tenantId = access.conversation().getTenantId();

        if (Objects.equals(access.userId(), targetUserId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "You are already a member of this conversation");
        }
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        tenantMemberRepository.findByTenantIdAndUserId(tenantId, targetUserId)
                .filter(com.learnerview.chitchat.tenant.TenantMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_ACCESS_DENIED,
                        "User is not a member of this workspace"));

        ConversationMember existing = memberRepository
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElse(null);

        if (existing != null && existing.isActive()) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER, "User is already a participant");
        }

        if (access.conversation().getType() == ConversationType.GROUP) {
            long activeMembers = memberRepository.countByConversationIdAndLeftAtIsNull(conversationId);
            if (activeMembers + 1 > maxGroupMembers) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Group conversations are limited to " + maxGroupMembers + " members");
            }
        }

        ConversationMember member;
        if (existing == null) {
            member = newMember(tenantId, conversationId, targetUserId, ConversationMember.Role.MEMBER);
        } else {
            existing.setLeftAt(null);
            existing.setJoinedAt(LocalDateTime.now());
            member = existing;
        }
        memberRepository.save(member);

        touch(access.conversation());
        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.MEMBER_ADDED, tenantId, conversationId, null,
                Map.of("userId", targetUserId, "role", member.getRole().name())));

        return detail(access.conversation(), access.userId());
    }

    @Override
    public ConversationResponse removeMember(String conversationId, String targetUserId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationRole(conversationId, ConversationMember.Role.OWNER);

        ConversationMember target = memberRepository
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .filter(ConversationMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "User is not an active participant"));

        if (target.getRole() == ConversationMember.Role.OWNER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Owner cannot be removed. Transfer ownership first");
        }

        target.setLeftAt(LocalDateTime.now());
        memberRepository.save(target);

        touch(access.conversation());
        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.MEMBER_REMOVED, access.conversation().getTenantId(), conversationId, null,
                Map.of("userId", targetUserId, "removedBy", access.userId())));

        return detail(access.conversation(), access.userId());
    }

    @Override
    public ConversationResponse transferOwnership(String conversationId, String newOwnerUserId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationRole(conversationId, ConversationMember.Role.OWNER);

        if (Objects.equals(access.userId(), newOwnerUserId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "New owner must be different from current owner");
        }

        ConversationMember newOwner = memberRepository
                .findByConversationIdAndUserId(conversationId, newOwnerUserId)
                .filter(ConversationMember::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "New owner must be an active participant"));

        // Promote first, demote second: a failure between the two writes leaves
        // two owners (recoverable) instead of zero owners (unmanageable).
        newOwner.setRole(ConversationMember.Role.OWNER);
        memberRepository.save(newOwner);
        access.member().setRole(ConversationMember.Role.ADMIN);
        memberRepository.save(access.member());

        // createdBy stays the original creator; ownership lives solely in
        // ConversationMember.role = OWNER.
        Conversation conversation = access.conversation();
        touch(conversation);

        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.MEMBER_ROLE_CHANGED, access.conversation().getTenantId(),
                conversationId, null,
                Map.of(
                        "userId", newOwnerUserId,
                        "role", ConversationMember.Role.OWNER.name(),
                        "previousOwnerId", access.userId())));

        return detail(conversation, access.userId());
    }

    @Override
    public void leaveConversation(String conversationId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);

        if (access.conversation().getType() == ConversationType.GROUP
                && access.member().getRole() == ConversationMember.Role.OWNER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Owner cannot leave. Transfer ownership first");
        }

        access.member().setLeftAt(LocalDateTime.now());
        memberRepository.save(access.member());
        touch(access.conversation());

        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.MEMBER_REMOVED, access.conversation().getTenantId(), conversationId, null,
                Map.of("userId", access.userId(), "reason", "left")));
    }

    @Override
    public void deleteConversation(String conversationId) {
        // Groups: owner only. DMs: either participant (they are peers, no owner exists).
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        if (access.conversation().getType() == ConversationType.GROUP
                && access.member().getRole() != ConversationMember.Role.OWNER) {
            throw new ApiException(ErrorCode.INSUFFICIENT_CONVERSATION_ROLE,
                    "Only the conversation owner can delete this conversation");
        }
        // Delete in dependency order: messages and members first (orphans are
        // harmless), conversation document last (once gone, nothing references it).
        mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)),
                com.learnerview.chitchat.message.Message.class);
        mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)),
                ConversationMember.class);
        conversationRepository.deleteById(conversationId);

        String tenantId = access.conversation().getTenantId();
        eventPublisherService.publish(tenantId, "conversation.deleted", Map.of(
                "conversationId", conversationId,
                "deletedBy", access.userId()
        ));
        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.CONVERSATION_DELETED, tenantId, conversationId, null,
                Map.of("deletedBy", access.userId())));
    }

    @Override
    public ConversationMemberResponse updateMemberSettings(String conversationId,
                                                           Boolean pinned,
                                                           Boolean muted,
                                                           Boolean archived,
                                                           ConversationMember.NotificationLevel notificationLevel) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);

        if (pinned == null && muted == null && archived == null && notificationLevel == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "At least one setting must be provided");
        }

        // Targeted updates on the caller's own membership only - no broadcast.
        ConversationMember member = access.member();
        if (pinned != null) {
            member.setPinned(pinned);
        }
        if (muted != null) {
            member.setMuted(muted);
        }
        if (archived != null) {
            member.setArchived(archived);
        }
        if (notificationLevel != null) {
            member.setNotificationLevel(notificationLevel);
        }
        memberRepository.save(member);
        return ConversationMemberResponse.from(member);
    }

    private ConversationMember newMember(String tenantId, String conversationId, String userId,
                                         ConversationMember.Role role) {
        return ConversationMember.builder()
                .tenantId(tenantId)
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .lastReadSequence(0L)
                .build();
    }

    /** Best-effort rollback of a partially created conversation. */
    private void compensateFailedCreation(String conversationId) {
        try {
            conversationRepository.deleteById(conversationId);
        } catch (Exception cleanupFailure) {
            log.warn("Failed to clean up conversation {} after creation failure: {}",
                    conversationId, cleanupFailure.getMessage());
        }
        removeOrphanedMembers(conversationId);
    }

    private void removeOrphanedMembers(String conversationId) {
        try {
            mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)),
                    ConversationMember.class);
        } catch (Exception cleanupFailure) {
            log.warn("Failed to clean up members of conversation {}: {}",
                    conversationId, cleanupFailure.getMessage());
        }
    }

    /**
     * Bumps only {@code updatedAt}. Never writes the loaded entity back: a
     * full-document save would rewind {@code lastMessageSequence} and
     * {@code lastMessageAt} written by concurrent message sends.
     */
    private void touch(Conversation conversation) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(conversation.getId())),
                new Update().set("updatedAt", LocalDateTime.now()),
                Conversation.class);
    }

    private ConversationResponse detail(Conversation conversation, String viewerUserId) {
        List<ConversationMember> members = memberRepository.findByConversationId(conversation.getId());
        ConversationMember viewer = members.stream()
                .filter(m -> m.getUserId().equals(viewerUserId))
                .findFirst()
                .orElse(null);
        return ConversationResponse.detail(conversation, viewer, members);
    }
}
