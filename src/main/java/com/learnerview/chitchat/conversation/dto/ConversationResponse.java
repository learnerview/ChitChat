package com.learnerview.chitchat.conversation.dto;

import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationStatus;
import com.learnerview.chitchat.conversation.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        String id,
        String tenantId,
        ConversationType type,
        ConversationStatus status,
        String name,
        String createdBy,
        String directKey,
        long lastMessageSequence,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long unreadCount,
        Long lastReadSequence,
        boolean pinned,
        boolean muted,
        boolean archived,
        ConversationMember.NotificationLevel notificationLevel,
        List<ConversationMemberResponse> members
) {

    public static ConversationResponse summary(Conversation conversation, ConversationMember viewer) {
        long lastRead = viewer == null ? 0L : viewer.getLastReadSequence();
        long latest = conversation.getLastMessageSequence();
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTenantId(),
                conversation.getType(),
                conversation.getStatus() == null ? ConversationStatus.ACTIVE : conversation.getStatus(),
                conversation.getName(),
                conversation.getCreatedBy(),
                conversation.getDirectKey(),
                latest,
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                Math.max(0L, latest - lastRead),
                viewer == null ? null : lastRead,
                viewer != null && viewer.isPinned(),
                viewer != null && viewer.isMuted(),
                viewer != null && viewer.isArchived(),
                viewer == null ? ConversationMember.NotificationLevel.ALL : viewer.getNotificationLevel(),
                List.of());
    }

    public static ConversationResponse detail(Conversation conversation,
                                              ConversationMember viewer,
                                              List<ConversationMember> members) {
        ConversationResponse summary = summary(conversation, viewer);
        return new ConversationResponse(
                summary.id(),
                summary.tenantId(),
                summary.type(),
                summary.status(),
                summary.name(),
                summary.createdBy(),
                summary.directKey(),
                summary.lastMessageSequence(),
                summary.lastMessageAt(),
                summary.createdAt(),
                summary.updatedAt(),
                summary.unreadCount(),
                summary.lastReadSequence(),
                summary.pinned(),
                summary.muted(),
                summary.archived(),
                summary.notificationLevel(),
                members.stream().map(ConversationMemberResponse::from).toList());
    }
}
