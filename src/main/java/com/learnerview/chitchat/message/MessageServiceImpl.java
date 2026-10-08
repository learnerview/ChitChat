package com.learnerview.chitchat.message;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.event.EventPublisherService;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.message.dto.EditMessageRequest;
import com.learnerview.chitchat.message.dto.MarkReadRequest;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.ReadStateResponse;
import com.learnerview.chitchat.message.dto.SendMessageRequest;
import com.learnerview.chitchat.realtime.RealtimeEvent;
import com.learnerview.chitchat.realtime.RealtimeEventPublisher;
import com.learnerview.chitchat.realtime.RealtimeEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MessageServiceImpl implements MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SEARCH_RESULT_LIMIT = 50;

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository memberRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthorizationService authorizationService;
    private final EventPublisherService eventPublisherService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Value("${app.limits.max-message-length:5000}")
    private int maxMessageLength;

    public MessageServiceImpl(MessageRepository messageRepository,
                              ConversationMemberRepository memberRepository,
                              MongoTemplate mongoTemplate,
                              AuthorizationService authorizationService,
                              EventPublisherService eventPublisherService,
                              RealtimeEventPublisher realtimeEventPublisher) {
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.mongoTemplate = mongoTemplate;
        this.authorizationService = authorizationService;
        this.eventPublisherService = eventPublisherService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    /**
     * Order of operations for a durable send:
     * validate membership -> idempotency check -> allocate sequence ->
     * persist message -> notify (webhook + realtime).
     */
    @Override
    public MessageResponse send(String conversationId, SendMessageRequest request) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        Conversation conversation = access.conversation();
        String tenantId = conversation.getTenantId();
        String senderId = access.userId();

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Message content is required");
        }
        if (content.length() > maxMessageLength) {
            throw new ApiException(ErrorCode.MESSAGE_TOO_LONG,
                    "Message content must be at most " + maxMessageLength + " characters");
        }

        String clientMessageId = normalize(request.getClientMessageId());
        if (clientMessageId != null) {
            Message existing = messageRepository
                    .findByTenantIdAndSenderIdAndClientMessageId(tenantId, senderId, clientMessageId)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getConversationId().equals(conversationId)
                        || !Objects.equals(existing.getContent(), content)
                        || !Objects.equals(existing.getReplyToId(), normalize(request.getReplyToId()))) {
                    throw new ApiException(ErrorCode.CLIENT_MESSAGE_ID_CONFLICT);
                }
                return MessageResponse.from(existing);
            }
        }

        if (request.getReplyToId() != null && !request.getReplyToId().isBlank()) {
            Message parent = messageRepository.findByIdAndTenantId(request.getReplyToId().trim(), tenantId)
                    .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND, "Reply target message not found"));
            if (!parent.getConversationId().equals(conversationId)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Reply target belongs to a different conversation");
            }
        }

        Message message = Message.builder()
                .tenantId(tenantId)
                .conversationId(conversationId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .content(content)
                .replyToId(normalize(request.getReplyToId()))
                .createdAt(LocalDateTime.now())
                .build();

        try {
            long sequence = allocateSequence(conversationId);
            message.setSequence(sequence);
            Message saved = messageRepository.save(message);

            publishIntegrationEvent(tenantId, "message.sent", saved);
            realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                    RealtimeEventType.MESSAGE_CREATED, tenantId, conversationId, saved.getSequence(),
                    MessageResponse.from(saved)));

            return MessageResponse.from(saved);
        } catch (DuplicateKeyException ex) {
            // Either a concurrent retry with the same clientMessageId won the race,
            // or the sequence was already consumed. Resolve idempotently.
            if (clientMessageId != null) {
                Message winner = messageRepository
                        .findByTenantIdAndSenderIdAndClientMessageId(tenantId, senderId, clientMessageId)
                        .orElse(null);
                if (winner != null) {
                    if (!winner.getConversationId().equals(conversationId)
                            || !Objects.equals(winner.getContent(), content)
                            || !Objects.equals(winner.getReplyToId(), normalize(request.getReplyToId()))) {
                        throw new ApiException(ErrorCode.CLIENT_MESSAGE_ID_CONFLICT);
                    }
                    return MessageResponse.from(winner);
                }
            }
            throw new ApiException(ErrorCode.CONFLICT, "Message could not be stored due to a concurrent update");
        }
    }

    /**
     * Atomically increments (and timestamps) the conversation's sequence counter.
     * Single-document update, so MongoDB guarantees uniqueness without transactions.
     */
    private long allocateSequence(String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(conversationId));
        Update update = new Update()
                .inc("lastMessageSequence", 1)
                .set("lastMessageAt", LocalDateTime.now())
                .set("updatedAt", LocalDateTime.now());

        Conversation updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Conversation.class);

        if (updated == null) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return updated.getLastMessageSequence();
    }

    @Override
    public MessagePageResponse getMessages(String conversationId, Long before, Long after, Integer limit) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        String tenantId = access.conversation().getTenantId();

        if (before != null && after != null) {
            throw new ApiException(ErrorCode.INVALID_CURSOR, "Use either before or after, not both");
        }
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        boolean forward = after != null;
        long cursor = forward
                ? Math.max(after, 0L)
                : (before == null ? Long.MAX_VALUE : Math.max(before, 0L));

        Sort sort = Sort.by(Sort.Direction.DESC, "sequence");
        if (forward) {
            sort = Sort.by(Sort.Direction.ASC, "sequence");
        }
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1, sort);

        List<Message> fetched = forward
                ? messageRepository.findByTenantIdAndConversationIdAndSequenceGreaterThan(
                        tenantId, conversationId, cursor, pageRequest)
                : messageRepository.findByTenantIdAndConversationIdAndSequenceLessThan(
                        tenantId, conversationId, cursor, pageRequest);

        boolean hasMore = fetched.size() > pageSize;
        if (hasMore) {
            fetched = fetched.subList(0, pageSize);
        }

        Long nextCursor = hasMore && !fetched.isEmpty()
                ? fetched.get(fetched.size() - 1).getSequence()
                : null;

        List<Message> ascending = new ArrayList<>(fetched);
        if (!forward) {
            Collections.reverse(ascending);
        }

        return new MessagePageResponse(
                ascending.stream().map(MessageResponse::from).toList(),
                hasMore,
                nextCursor);
    }

    @Override
    public void markAsRead(String conversationId, MarkReadRequest request) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        Conversation conversation = access.conversation();
        long latest = conversation.getLastMessageSequence();

        long target = request != null && request.sequence() != null
                ? Math.min(Math.max(request.sequence(), 0L), latest)
                : latest;

        // $max keeps the cursor monotonic even with concurrent writers.
        Query query = Query.query(Criteria
                .where("tenantId").is(conversation.getTenantId())
                .and("conversationId").is(conversationId)
                .and("userId").is(access.userId()));
        Update update = new Update().max("lastReadSequence", target);
        mongoTemplate.updateFirst(query, update, ConversationMember.class);

        long publishedCursor = Math.max(access.member().getLastReadSequence(), target);
        realtimeEventPublisher.publish(conversationId, RealtimeEvent.of(
                RealtimeEventType.READ_UPDATED, conversation.getTenantId(), conversationId, null,
                Map.of("userId", access.userId(), "lastReadSequence", publishedCursor)));
    }

    @Override
    public ReadStateResponse getReadState(String conversationId) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        long latest = access.conversation().getLastMessageSequence();
        long lastRead = access.member().getLastReadSequence();
        return new ReadStateResponse(conversationId, lastRead, latest, Math.max(0L, latest - lastRead));
    }

    @Override
    public MessageResponse editMessage(String messageId, EditMessageRequest request) {
        Message message = requireMessage(messageId);
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(message.getConversationId());

        if (!message.getSenderId().equals(access.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the sender can edit this message");
        }
        if (message.isDeleted()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Deleted message cannot be edited");
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Message content is required");
        }
        if (content.length() > maxMessageLength) {
            throw new ApiException(ErrorCode.MESSAGE_TOO_LONG,
                    "Message content must be at most " + maxMessageLength + " characters");
        }

        // Atomic conditional update: a concurrent delete or edit can never be
        // silently overwritten (or resurrect a deleted message) by a full-document write.
        LocalDateTime editedAt = LocalDateTime.now();
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(message.getId())
                        .and("tenantId").is(message.getTenantId())
                        .and("deletedAt").is(null)),
                new Update().set("content", content).set("editedAt", editedAt),
                Message.class);
        if (result.getModifiedCount() == 0) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Message was deleted or edited concurrently; fetch and retry");
        }
        message.setContent(content);
        message.setEditedAt(editedAt);
        Message saved = message;

        publishIntegrationEvent(access.conversation().getTenantId(), "message.updated", saved);
        realtimeEventPublisher.publish(saved.getConversationId(), RealtimeEvent.of(
                RealtimeEventType.MESSAGE_EDITED, saved.getTenantId(), saved.getConversationId(),
                saved.getSequence(), MessageResponse.from(saved)));

        return MessageResponse.from(saved);
    }

    @Override
    public void deleteMessage(String messageId) {
        Message message = requireMessage(messageId);
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(message.getConversationId());

        boolean isSender = access.userId().equals(message.getSenderId());
        boolean isOwner = access.member().getRole() == ConversationMember.Role.OWNER;
        if (!isSender && !isOwner) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only the sender or conversation owner can delete this message");
        }
        if (message.isDeleted()) {
            return;
        }

        // Atomic conditional update - deleting an already-deleted message is a no-op.
        LocalDateTime deletedAt = LocalDateTime.now();
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(message.getId())
                        .and("tenantId").is(message.getTenantId())
                        .and("deletedAt").is(null)),
                new Update().set("deletedAt", deletedAt),
                Message.class);
        if (result.getModifiedCount() == 0) {
            return;
        }
        message.setDeletedAt(deletedAt);
        Message saved = message;

        publishIntegrationEvent(access.conversation().getTenantId(), "message.deleted", saved);
        realtimeEventPublisher.publish(saved.getConversationId(), RealtimeEvent.of(
                RealtimeEventType.MESSAGE_DELETED, saved.getTenantId(), saved.getConversationId(),
                saved.getSequence(),
                Map.of("messageId", saved.getId(), "deletedBy", access.userId())));
    }

    @Override
    public List<MessageResponse> searchInConversation(String conversationId, String query) {
        AuthorizationService.ConversationAccess access =
                authorizationService.requireConversationAccess(conversationId);
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return messageRepository.findByTenantIdAndConversationIdAndDeletedAtIsNullAndContentContainingIgnoreCase(
                        access.conversation().getTenantId(),
                        conversationId,
                        query.trim(),
                        PageRequest.of(0, SEARCH_RESULT_LIMIT, Sort.by(Sort.Direction.DESC, "sequence")))
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Override
    public List<MessageResponse> searchAll(String query) {
        String tenantId = TenantContext.getRequiredTenantId();
        authorizationService.requireTenantMember(tenantId);
        String userId = authorizationService.currentUserId();
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> conversationIds = memberRepository
                .findByTenantIdAndUserIdAndLeftAtIsNull(tenantId, userId).stream()
                .map(ConversationMember::getConversationId)
                .toList();
        if (conversationIds.isEmpty()) {
            return List.of();
        }

        return messageRepository.findByTenantIdAndConversationIdInAndDeletedAtIsNullAndContentContainingIgnoreCase(
                        tenantId, conversationIds, query.trim(),
                        PageRequest.of(0, SEARCH_RESULT_LIMIT, Sort.by(Sort.Direction.DESC, "sequence")))
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    private Message requireMessage(String messageId) {
        String tenantId = TenantContext.getRequiredTenantId();
        return messageRepository.findByIdAndTenantId(messageId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    private void publishIntegrationEvent(String tenantId, String event, Message message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", message.getId());
        payload.put("conversationId", message.getConversationId());
        payload.put("senderId", message.getSenderId());
        payload.put("sequence", message.getSequence());
        if (message.getReplyToId() != null) {
            payload.put("replyToId", message.getReplyToId());
        }
        eventPublisherService.publish(tenantId, event, payload);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
