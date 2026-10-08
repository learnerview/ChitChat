package com.learnerview.chitchat.message;

import com.learnerview.chitchat.authorization.AuthorizationService;
import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.common.event.EventPublisherService;
import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.conversation.Conversation;
import com.learnerview.chitchat.conversation.ConversationMember;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.conversation.ConversationType;
import com.learnerview.chitchat.message.dto.EditMessageRequest;
import com.learnerview.chitchat.message.dto.MarkReadRequest;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.ReadStateResponse;
import com.learnerview.chitchat.message.dto.SendMessageRequest;
import com.mongodb.client.result.UpdateResult;
import com.learnerview.chitchat.realtime.RealtimeEvent;
import com.learnerview.chitchat.realtime.RealtimeEventPublisher;
import com.learnerview.chitchat.realtime.RealtimeEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    private static final String TENANT = "tenant-1";
    private static final String CONVERSATION = "conv-1";
    private static final String USER = "user-1";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationMemberRepository memberRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        ReflectionTestUtils.setField(messageService, "maxMessageLength", 5000);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void repeatedClientMessageIdReturnsTheOriginalMessageWithoutSavingAgain() {
        stubAccess();
        Message original = message(1042L, "hello");
        when(messageRepository.findByTenantIdAndSenderIdAndClientMessageId(TENANT, USER, "cmid-1"))
                .thenReturn(Optional.of(original));

        MessageResponse response = messageService.send(CONVERSATION,
                new SendMessageRequest("hello", null, "cmid-1"));

        assertThat(response.sequence()).isEqualTo(1042L);
        assertThat(response.content()).isEqualTo("hello");
        verify(messageRepository, never()).save(any(Message.class));
        verify(realtimeEventPublisher, never()).publish(anyString(), any(RealtimeEvent.class));
    }

    @Test
    void clientMessageIdReusedWithDifferentContentConflicts() {
        stubAccess();
        Message original = message(1042L, "hello");
        when(messageRepository.findByTenantIdAndSenderIdAndClientMessageId(TENANT, USER, "cmid-1"))
                .thenReturn(Optional.of(original));

        assertThatThrownBy(() -> messageService.send(CONVERSATION,
                new SendMessageRequest("hello retry", null, "cmid-1")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_MESSAGE_ID_CONFLICT);

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void concurrentRetryResolvesToTheWinnerViaDuplicateKeyRecovery() {
        stubAccess();
        Message winner = message(1043L, "hello");
        when(messageRepository.findByTenantIdAndSenderIdAndClientMessageId(TENANT, USER, "cmid-9"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversationWithSequence(1043L));
        when(messageRepository.save(any(Message.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        MessageResponse response = messageService.send(CONVERSATION,
                new SendMessageRequest("hello", null, "cmid-9"));

        assertThat(response.id()).isEqualTo(winner.getId());
        assertThat(response.sequence()).isEqualTo(1043L);
    }

    @Test
    void duplicateKeyWithoutAWinnerBecomesAConflict() {
        stubAccess();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversationWithSequence(101L));
        when(messageRepository.save(any(Message.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThatThrownBy(() -> messageService.send(CONVERSATION,
                new SendMessageRequest("hello", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void markAsReadAdvancesTheCursorAtomicallyClampedToTheHighWaterMark() {
        stubAccess();
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ConversationMember.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        messageService.markAsRead(CONVERSATION, new MarkReadRequest(500L));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(),
                eq(ConversationMember.class));

        assertThat(queryCaptor.getValue().getQueryObject().get("tenantId")).isEqualTo(TENANT);
        assertThat(queryCaptor.getValue().getQueryObject().get("conversationId")).isEqualTo(CONVERSATION);
        org.bson.Document max = (org.bson.Document) updateCaptor.getValue().getUpdateObject().get("$max");
        assertThat(max.get("lastReadSequence")).isEqualTo(100L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RealtimeEventType.READ_UPDATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) eventCaptor.getValue().payload();
        assertThat(payload.get("userId")).isEqualTo(USER);
        assertThat(payload.get("lastReadSequence")).isEqualTo(100L);
    }

    @Test
    void getReadStateReportsTheUnreadCountFromTheSequenceHighWaterMark() {
        stubAccess();

        ReadStateResponse state = messageService.getReadState(CONVERSATION);

        assertThat(state.conversationId()).isEqualTo(CONVERSATION);
        assertThat(state.lastReadSequence()).isEqualTo(0L);
        assertThat(state.latestSequence()).isEqualTo(100L);
        assertThat(state.unreadCount()).isEqualTo(100L);
    }

    @Test
    void editMessageAppliesAnAtomicConditionalUpdate() {
        stubAccess();
        Message original = message(1042L, "old content");
        when(messageRepository.findByIdAndTenantId(original.getId(), TENANT))
                .thenReturn(Optional.of(original));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        MessageResponse response = messageService.editMessage(original.getId(),
                new EditMessageRequest("new content"));

        assertThat(response.content()).isEqualTo("new content");
        assertThat(response.edited()).isTrue();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), any(Update.class), eq(Message.class));
        assertThat(queryCaptor.getValue().getQueryObject().containsKey("deletedAt")).isTrue();

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RealtimeEventType.MESSAGE_EDITED);
    }

    @Test
    void editMessageFailsWhenTheMessageWasDeletedConcurrently() {
        stubAccess();
        Message original = message(1042L, "old content");
        when(messageRepository.findByIdAndTenantId(original.getId(), TENANT))
                .thenReturn(Optional.of(original));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> messageService.editMessage(original.getId(),
                new EditMessageRequest("new content")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(realtimeEventPublisher, never()).publish(anyString(), any(RealtimeEvent.class));
    }

    @Test
    void deleteMessageIsIdempotentAgainstConcurrentDeletes() {
        stubAccess();
        Message original = message(1042L, "content");
        when(messageRepository.findByIdAndTenantId(original.getId(), TENANT))
                .thenReturn(Optional.of(original));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        messageService.deleteMessage(original.getId());

        verify(realtimeEventPublisher, never()).publish(anyString(), any(RealtimeEvent.class));
    }

    @Test
    void deleteMessagePublishesTheTombstoneEvent() {
        stubAccess();
        Message original = message(1042L, "content");
        when(messageRepository.findByIdAndTenantId(original.getId(), TENANT))
                .thenReturn(Optional.of(original));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        messageService.deleteMessage(original.getId());

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RealtimeEventType.MESSAGE_DELETED);
    }

    @Test
    void editMessageRejectsNonSenders() {
        stubAccess();
        Message foreign = message(1042L, "not yours");
        foreign.setSenderId("someone-else");
        when(messageRepository.findByIdAndTenantId(foreign.getId(), TENANT))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> messageService.editMessage(foreign.getId(),
                new EditMessageRequest("hijack")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Message.class));
    }

    @Test
    void editMessageRejectsDeletedMessages() {
        stubAccess();
        Message deleted = message(1042L, "gone");
        deleted.setDeletedAt(LocalDateTime.now());
        when(messageRepository.findByIdAndTenantId(deleted.getId(), TENANT))
                .thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> messageService.editMessage(deleted.getId(),
                new EditMessageRequest("revive")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Message.class));
    }

    @Test
    void deleteMessageAllowsTheConversationOwnerToDeleteOthersMessages() {
        stubAccessAs(ConversationMember.Role.OWNER);
        Message foreign = message(1042L, "moderate me");
        foreign.setSenderId("someone-else");
        when(messageRepository.findByIdAndTenantId(foreign.getId(), TENANT))
                .thenReturn(Optional.of(foreign));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        messageService.deleteMessage(foreign.getId());

        verify(realtimeEventPublisher).publish(eq(CONVERSATION), any(RealtimeEvent.class));
    }

    @Test
    void deleteMessageRejectsNonSenderNonOwner() {
        stubAccess();
        Message foreign = message(1042L, "not yours");
        foreign.setSenderId("someone-else");
        when(messageRepository.findByIdAndTenantId(foreign.getId(), TENANT))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> messageService.deleteMessage(foreign.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Message.class));
    }

    @Test
    void searchInConversationOnlyQueriesLiveMessages() {
        stubAccess();
        when(messageRepository
                .findByTenantIdAndConversationIdAndDeletedAtIsNullAndContentContainingIgnoreCase(
                        eq(TENANT), eq(CONVERSATION), eq("hello"), any(Pageable.class)))
                .thenReturn(List.of(message(5L, "hello world")));

        List<MessageResponse> results = messageService.searchInConversation(CONVERSATION, " hello ");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("hello world");
    }

    @Test
    void searchAllRequiresWorkspaceMembership() {
        when(authorizationService.requireTenantMember(TENANT))
                .thenThrow(new ApiException(ErrorCode.TENANT_ACCESS_DENIED));

        assertThatThrownBy(() -> messageService.searchAll("hello"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TENANT_ACCESS_DENIED);

        verify(messageRepository, never())
                .findByTenantIdAndConversationIdInAndDeletedAtIsNullAndContentContainingIgnoreCase(
                        anyString(), any(), anyString(), any(Pageable.class));
    }

    @Test
    void sendAllocatesSequenceFromTheConversationCounter() {
        stubAccess();
        when(messageRepository.findByTenantIdAndSenderIdAndClientMessageId(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Conversation.class)))
                .thenReturn(conversationWithSequence(7L));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId("message-1");
            return saved;
        });

        MessageResponse response = messageService.send(CONVERSATION,
                new SendMessageRequest("hello", null, "cmid-2"));

        assertThat(response.sequence()).isEqualTo(7L);
        assertThat(response.clientMessageId()).isEqualTo("cmid-2");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSequence()).isEqualTo(7L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publish(eq(CONVERSATION), eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RealtimeEventType.MESSAGE_CREATED);
        assertThat(eventCaptor.getValue().sequence()).isEqualTo(7L);
    }

    @Test
    void sendRejectsContentLongerThanTheConfiguredLimit() {
        stubAccess();
        String oversized = "x".repeat(5001);

        assertThatThrownBy(() -> messageService.send(CONVERSATION,
                new SendMessageRequest(oversized, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_TOO_LONG);

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendRejectsBlankContent() {
        stubAccess();

        assertThatThrownBy(() -> messageService.send(CONVERSATION,
                new SendMessageRequest("   ", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void olderPageIsReturnedAscendingWithACursorForTheNextPage() {
        stubAccess();
        // Descending fetch of limit+1 (limit = 4)
        List<Message> fetched = List.of(
                message(60L, "60"), message(59L, "59"),
                message(58L, "58"), message(57L, "57"), message(56L, "56"));
        when(messageRepository.findByTenantIdAndConversationIdAndSequenceLessThan(
                eq(TENANT), eq(CONVERSATION), eq(Long.MAX_VALUE), any(Pageable.class)))
                .thenReturn(fetched);

        MessagePageResponse page = messageService.getMessages(CONVERSATION, null, null, 4);

        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(57L);
        assertThat(page.messages()).extracting(MessageResponse::sequence)
                .containsExactly(57L, 58L, 59L, 60L);
    }

    @Test
    void forwardCursorReturnsMessagesAfterTheGivenSequence() {
        stubAccess();
        List<Message> fetched = List.of(message(1043L, "a"), message(1044L, "b"));
        when(messageRepository.findByTenantIdAndConversationIdAndSequenceGreaterThan(
                eq(TENANT), eq(CONVERSATION), eq(1042L), any(Pageable.class)))
                .thenReturn(fetched);

        MessagePageResponse page = messageService.getMessages(CONVERSATION, null, 1042L, 50);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.messages()).extracting(MessageResponse::sequence)
                .containsExactly(1043L, 1044L);
    }

    @Test
    void bothCursorsAreRejected() {
        stubAccess();

        assertThatThrownBy(() -> messageService.getMessages(CONVERSATION, 10L, 20L, 50))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    private void stubAccess() {
        stubAccessAs(ConversationMember.Role.MEMBER);
    }

    private void stubAccessAs(ConversationMember.Role role) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setTenantId(TENANT);
        conversation.setType(ConversationType.GROUP);
        conversation.setCreatedBy(USER);
        conversation.setLastMessageSequence(100L);

        ConversationMember member = ConversationMember.builder()
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .userId(USER)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();

        when(authorizationService.requireConversationAccess(CONVERSATION))
                .thenReturn(new AuthorizationService.ConversationAccess(conversation, member, USER));
    }

    private Conversation conversationWithSequence(long sequence) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setTenantId(TENANT);
        conversation.setLastMessageSequence(sequence);
        return conversation;
    }

    private Message message(long sequence, String content) {
        return Message.builder()
                .id("msg-" + sequence)
                .tenantId(TENANT)
                .conversationId(CONVERSATION)
                .senderId(USER)
                .sequence(sequence)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
