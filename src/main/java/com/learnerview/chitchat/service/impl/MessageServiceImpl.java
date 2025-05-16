package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.Message;
import com.learnerview.chitchat.entities.ConversationMembership;
import com.learnerview.chitchat.exception.MessageException;
import com.learnerview.chitchat.exception.ResourceNotFoundException;
import com.learnerview.chitchat.repositories.MessageRepository;
import com.learnerview.chitchat.repositories.ConversationMembershipRepository;
import com.learnerview.chitchat.service.MessageService;
import com.learnerview.chitchat.dto.PaginatedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationMembershipRepository membershipRepository;

    @Override
    public Message sendMessage(String conversationId, String sender, String content) {
        return sendMessage(conversationId, sender, content, null, null);
    }

    @Override
    public Message sendMessage(String conversationId, String sender, String content, String replyToId) {
        return sendMessage(conversationId, sender, content, replyToId, null);
    }

    @Override
    public Message sendMessage(String conversationId, String sender, String content, String replyToId, List<com.learnerview.chitchat.entities.Attachment> attachments) {
        try {
            // Validate inputs
            if (conversationId == null || conversationId.trim().isEmpty()) {
                throw new IllegalArgumentException("Conversation ID cannot be null or empty");
            }
            if (sender == null || sender.trim().isEmpty()) {
                throw new IllegalArgumentException("Sender cannot be null or empty");
            }
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("Message content cannot be null or empty");
            }
            if (content.length() > 5000) {
                throw new IllegalArgumentException("Message content too long (max 5000 characters)");
            }
            
            // Check if user has permission to send message
            ConversationMembership membership = membershipRepository
                    .findByConversationIdAndUsername(conversationId, sender)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CONVERSATION_NOT_FOUND", 
                            "You are not a member of this conversation"));
            
            // Create message
            Message message = new Message(conversationId, sender, content);
            message.setReplyToId(replyToId);
            message.setAttachments(attachments != null ? attachments : new ArrayList<>());
            
            // Parse mentions
            if (content != null) {
                Pattern pattern = Pattern.compile("@(\\w+)");
                Matcher matcher = pattern.matcher(content);
                List<String> mentions = new ArrayList<>();
                while (matcher.find()) {
                    mentions.add(matcher.group(1));
                }
                message.setMentions(mentions);
            }
            
            // Un-hide for everyone in the conversation
            membershipRepository.findByConversationId(conversationId).forEach(m -> {
                if (m.isHidden()) {
                    m.setHidden(false);
                    membershipRepository.save(m);
                }
            });
            
            return messageRepository.save(message);
            
        } catch (DataAccessException ex) {
            throw new MessageException("DATABASE_ERROR", "Failed to save message due to database error", ex);
        } catch (Exception ex) {
            log.error("Error sending message", ex);
            throw new MessageException("SEND_FAILED", "Failed to send message: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Message> getMessageHistory(String conversationId, String viewerUsername) {
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, viewerUsername)
                .orElse(null);
            
        LocalDateTime clearTime = membership != null ? membership.getLastClearedAt() : null;
        
        List<Message> messages = messageRepository.findByConversationIdOrderByTimeStampAsc(conversationId);
        return messages.stream()
                .filter(m -> !m.getDeletedBy().contains(viewerUsername))
                .filter(m -> clearTime == null || m.getTimeStamp().isAfter(clearTime))
                .toList();
    }

    @Override
    public PaginatedResponse<Message> getMessageHistoryPaginated(String conversationId, String viewerUsername, int page, int size) {
        ConversationMembership membership = membershipRepository
                .findByConversationIdAndUsername(conversationId, viewerUsername)
                .orElse(null);
            
        LocalDateTime clearTime = membership != null ? membership.getLastClearedAt() : null;
        
        // Create pageable request with sorting by timestamp descending (newest first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timeStamp"));
        
        // Get paginated messages
        Page<Message> messagePage = messageRepository.findByConversationId(conversationId, pageable);
        
        // Filter messages based on user permissions
        List<Message> filteredMessages = messagePage.getContent().stream()
                .filter(m -> !m.getDeletedBy().contains(viewerUsername))
                .filter(m -> clearTime == null || m.getTimeStamp().isAfter(clearTime))
                .toList();
        
        return PaginatedResponse.of(
                filteredMessages,
                page,
                size,
                messagePage.getTotalElements()
        );
    }

    @Override
    public void markAsRead(String conversationId, String username) {
        List<Message> messages = messageRepository.findByConversationIdAndSenderNotOrderByTimeStampDesc(conversationId, username);
        messages.stream()
                .filter(m -> !m.getReadBy().contains(username))
                .forEach(m -> {
                    m.getReadBy().add(username);
                    m.setReadAt(LocalDateTime.now());
                    m.setStatus(com.learnerview.chitchat.entities.MessageStatus.READ);
                    messageRepository.save(m);
                });
    }

    @Override
    public void deleteMessage(String messageId, String username, boolean deleteForEveryone) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException("MESSAGE_NOT_FOUND", "Message not found"));
        
        if (deleteForEveryone) {
            if (!message.getSender().equals(username)) {
                throw new MessageException("DELETE_PERMISSION_DENIED", "You can only delete your own messages for everyone");
            }
            
            LocalDateTime limit = message.getTimeStamp().plusMinutes(1);
            if (LocalDateTime.now().isAfter(limit)) {
                throw new MessageException("DELETE_TIME_EXPIRED", "Messages can only be deleted for everyone within 1 minute");
            }
            
            message.setContent("🚫 *This message was deleted*");
            message.setOriginalContent(null);
            message.setAttachments(new ArrayList<>());
            message.setEdited(false);
            messageRepository.save(message);
        } else {
            if (!message.getDeletedBy().contains(username)) {
                message.getDeletedBy().add(username);
                messageRepository.save(message);
            }
        }
    }

    @Override
    public Message editMessage(String messageId, String newContent, String username) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException("MESSAGE_NOT_FOUND", "Message not found"));
        
        if (!message.getSender().equals(username)) {
            throw new MessageException("EDIT_PERMISSION_DENIED", "You can only edit your own messages");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesLater = message.getTimeStamp().plusMinutes(5);
        
        if (now.isAfter(fiveMinutesLater)) {
            throw new MessageException("EDIT_TIME_EXPIRED", "Messages can only be edited within 5 minutes");
        }
        
        if (message.getContent().equals(newContent)) {
            return message;
        }
        
        if (!message.isEdited()) {
            message.setOriginalContent(message.getContent());
        }
        
        message.setContent(newContent);
        message.setEdited(true);
        message.setEditedAt(now);
        
        return messageRepository.save(message);
    }

    @Override
    public Message forwardMessage(String messageId, String toConversationId, String sender) {
        Message original = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException("MESSAGE_NOT_FOUND", "Original message not found"));
        
        if (original.getContent().contains("🚫 *This message was deleted*")) {
            throw new MessageException("CANNOT_FORWARD_DELETED", "Cannot forward deleted messages");
        }

        Message forwarded = new Message(toConversationId, sender, original.getContent());
        forwarded.setForwarded(true);
        if (original.getAttachments() != null) {
            forwarded.setAttachments(new ArrayList<>(original.getAttachments()));
        }
        
        return messageRepository.save(forwarded);
    }

    @Override
    public Message getMessage(String id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new MessageException("MESSAGE_NOT_FOUND", "Message not found"));
    }
}
