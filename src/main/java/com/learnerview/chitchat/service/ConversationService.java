package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Conversation;

import java.util.List;
import java.util.Set;

public interface ConversationService {
    Conversation createDirectConversation(String currentUser, String otherUser);

    Conversation createGroupConversation(String currentUser, String name, Set<String> members);

    List<Conversation> listForUser(String username);

    Conversation getForUser(String conversationId, String username);

    Conversation renameConversation(String conversationId, String username, String newName);

    Conversation addParticipant(String conversationId, String requester, String participantUsername);

    Conversation removeParticipant(String conversationId, String requester, String participantUsername);

    Conversation transferOwnership(String conversationId, String username, String newOwner);

    void leaveConversation(String conversationId, String username);
}