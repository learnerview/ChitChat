package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Conversation;
import com.learnerview.chitchat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/dm")
    public Conversation createDM(@RequestParam String withUser, Authentication auth) {
        return conversationService.createDM(auth.getName(), withUser);
    }

    @PostMapping("/group")
    public Conversation createGroup(
            @RequestParam String name, 
            @RequestParam boolean isPublic, 
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String handle,
            Authentication auth) {
        return conversationService.createGroup(name, auth.getName(), isPublic, description, handle);
    }

    @PostMapping("/private-group")
    public Conversation createPrivateGroup(
            @RequestParam String name, 
            @RequestParam(required = false) String description,
            Authentication auth) {
        return conversationService.createGroup(name, auth.getName(), false, description, null);
    }

    @GetMapping("/my")
    public List<Conversation> getMyConversations(Authentication auth) {
        return conversationService.getMyConversations(auth.getName());
    }

    @GetMapping("/{id}")
    public Conversation getConversation(@PathVariable String id) {
        return conversationService.getConversation(id);
    }

    @GetMapping("/public")
    public List<Conversation> getPublicConversations(Authentication auth) {
        return conversationService.getPublicConversations(auth.getName());
    }

    @PostMapping("/{id}/join")
    public Conversation joinPublicConversation(@PathVariable String id, Authentication auth) {
        return conversationService.joinPublicConversation(id, auth.getName());
    }

    @DeleteMapping("/{id}")
    public void deleteConversation(@PathVariable String id, Authentication auth) {
        conversationService.deleteConversation(id, auth.getName());
    }

    @PostMapping("/{id}/leave")
    public void leaveConversation(@PathVariable String id, Authentication auth) {
        conversationService.leaveConversation(id, auth.getName());
    }

    @PutMapping("/{id}/settings")
    public Conversation updateSettings(@PathVariable String id, @RequestParam boolean adminOnlyMessaging, Authentication auth) {
        return conversationService.updateSettings(id, adminOnlyMessaging, auth.getName());
    }

    @PutMapping("/{id}")
    public Conversation updateGroup(@PathVariable String id, @Valid @RequestBody Conversation updates, Authentication auth) {
        return conversationService.updateGroup(id, updates.getName(), updates.getDescription(), auth.getName());
    }

    @PostMapping("/{id}/invite")
    public String generateInviteLink(@PathVariable String id, Authentication auth) {
        return conversationService.generateInviteLink(id, auth.getName());
    }

    @DeleteMapping("/{id}/invite")
    public void revokeInviteLink(@PathVariable String id, Authentication auth) {
        conversationService.revokeInviteLink(id, auth.getName());
    }

    @PostMapping("/join/{inviteCode}")
    public Conversation joinViaInviteLink(@PathVariable String inviteCode, Authentication auth) {
        return conversationService.joinViaInviteLink(inviteCode, auth.getName());
    }

    @PostMapping("/{id}/pin")
    public void togglePin(@PathVariable String id, Authentication auth) {
        conversationService.togglePin(id, auth.getName());
    }

    @PostMapping("/{id}/mute")
    public void toggleMute(@PathVariable String id, Authentication auth) {
        conversationService.toggleMute(id, auth.getName());
    }
}
