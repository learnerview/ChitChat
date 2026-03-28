package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.service.ConversationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/dm")
    public Conversation createDM(@RequestParam("with") String withUser, Authentication auth) {
        return conversationService.createDirectConversation(auth.getName(), withUser);
    }

    @PostMapping("/group")
    public Conversation createGroup(@RequestParam String name,
                                    @RequestBody(required = false) Set<String> members,
                                    Authentication auth) {
        return conversationService.createGroupConversation(auth.getName(), name, members);
    }

    @GetMapping
    public List<Conversation> listMyConversations(Authentication auth) {
        return conversationService.listForUser(auth.getName());
    }

    @GetMapping("/{id}")
    public Conversation getConversation(@PathVariable String id, Authentication auth) {
        return conversationService.getForUser(id, auth.getName());
    }

    @PatchMapping("/{id}/name")
    public Conversation renameConversation(@PathVariable String id,
                                           @RequestParam String name,
                                           Authentication auth) {
        return conversationService.renameConversation(id, auth.getName(), name);
    }

    @PostMapping("/{id}/participants")
    public Conversation addParticipant(@PathVariable String id,
                                       @RequestParam("username") String participantUsername,
                                       Authentication auth) {
        return conversationService.addParticipant(id, auth.getName(), participantUsername);
    }

    @DeleteMapping("/{id}/participants/{username}")
    public Conversation removeParticipant(@PathVariable String id,
                                          @PathVariable String username,
                                          Authentication auth) {
        return conversationService.removeParticipant(id, auth.getName(), username);
    }

    @PostMapping("/{id}/leave")
    public void leaveConversation(@PathVariable String id, Authentication auth) {
        conversationService.leaveConversation(id, auth.getName());
    }

    @PostMapping("/{id}/transfer")
    public Conversation transferOwnership(@PathVariable String id,
                                          @RequestParam("to") String newOwner,
                                          Authentication auth) {
        return conversationService.transferOwnership(id, auth.getName(), newOwner);
    }
}
