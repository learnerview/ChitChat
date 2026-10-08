package com.learnerview.chitchat.conversation;

import com.learnerview.chitchat.conversation.dto.ConversationMemberResponse;
import com.learnerview.chitchat.conversation.dto.ConversationResponse;
import com.learnerview.chitchat.conversation.dto.CreateDirectConversationRequest;
import com.learnerview.chitchat.conversation.dto.CreateGroupConversationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public record RenameRequest(@NotBlank(message = "Conversation name is required") String name) {
    }

    public record MemberRequest(@NotBlank(message = "userId is required") String userId) {
    }

    @PostMapping("/dm")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createDM(@Valid @RequestBody CreateDirectConversationRequest request) {
        return conversationService.createDirectConversation(request.userId());
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createGroup(@Valid @RequestBody CreateGroupConversationRequest request) {
        return conversationService.createGroupConversation(request.name(), request.memberIds());
    }

    @GetMapping
    public List<ConversationResponse> listMyConversations() {
        return conversationService.listForUser();
    }

    @GetMapping("/{id}")
    public ConversationResponse getConversation(@PathVariable String id) {
        return conversationService.getForUser(id);
    }

    @PatchMapping("/{id}/name")
    public ConversationResponse renameConversation(@PathVariable String id,
                                                   @Valid @RequestBody RenameRequest request) {
        return conversationService.renameConversation(id, request.name());
    }

    @PostMapping("/{id}/members")
    public ConversationResponse addMember(@PathVariable String id,
                                          @Valid @RequestBody MemberRequest request) {
        return conversationService.addMember(id, request.userId().trim());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ConversationResponse removeMember(@PathVariable String id,
                                             @PathVariable String userId) {
        return conversationService.removeMember(id, userId);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveConversation(@PathVariable String id) {
        conversationService.leaveConversation(id);
    }

    @PostMapping("/{id}/transfer")
    public ConversationResponse transferOwnership(@PathVariable String id,
                                                  @Valid @RequestBody MemberRequest request) {
        return conversationService.transferOwnership(id, request.userId().trim());
    }

    public record UpdateMemberSettingsRequest(
            Boolean pinned,
            Boolean muted,
            Boolean archived,
            ConversationMember.NotificationLevel notificationLevel
    ) {
    }

    @PatchMapping("/{id}/settings")
    public ConversationMemberResponse updateMemberSettings(@PathVariable String id,
                                                           @RequestBody UpdateMemberSettingsRequest request) {
        return conversationService.updateMemberSettings(id,
                request.pinned(), request.muted(), request.archived(), request.notificationLevel());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
    }
}
