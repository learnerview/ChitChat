package com.learnerview.chitchat.conversation.dto;

import com.learnerview.chitchat.conversation.ConversationMember;

import java.time.LocalDateTime;

public record ConversationMemberResponse(
        String userId,
        ConversationMember.Role role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        long lastReadSequence,
        boolean muted,
        boolean archived,
        boolean pinned,
        ConversationMember.NotificationLevel notificationLevel
) {
    public static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getUserId(),
                member.getRole(),
                member.getJoinedAt(),
                member.getLeftAt(),
                member.getLastReadSequence(),
                member.isMuted(),
                member.isArchived(),
                member.isPinned(),
                member.getNotificationLevel());
    }
}
