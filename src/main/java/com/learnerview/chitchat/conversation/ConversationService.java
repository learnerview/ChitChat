package com.learnerview.chitchat.conversation;

import com.learnerview.chitchat.conversation.dto.ConversationMemberResponse;
import com.learnerview.chitchat.conversation.dto.ConversationResponse;

import java.util.List;
import java.util.Set;

public interface ConversationService {

    ConversationResponse createDirectConversation(String otherUserId);

    ConversationResponse createGroupConversation(String name, Set<String> memberIds);

    List<ConversationResponse> listForUser();

    ConversationResponse getForUser(String conversationId);

    ConversationResponse renameConversation(String conversationId, String newName);

    ConversationResponse addMember(String conversationId, String targetUserId);

    ConversationResponse removeMember(String conversationId, String targetUserId);

    ConversationResponse transferOwnership(String conversationId, String newOwnerUserId);

    void leaveConversation(String conversationId);

    void deleteConversation(String conversationId);

    ConversationMemberResponse updateMemberSettings(String conversationId,
                                                     Boolean pinned,
                                                     Boolean muted,
                                                     Boolean archived,
                                                     ConversationMember.NotificationLevel notificationLevel);
}
