package com.learnerview.chitchat.realtime;

import com.learnerview.chitchat.common.tenancy.TenantContext;
import com.learnerview.chitchat.message.MessageService;
import com.learnerview.chitchat.message.dto.MessagePageResponse;
import com.learnerview.chitchat.message.dto.MessageResponse;
import com.learnerview.chitchat.message.dto.ReadStateResponse;
import com.learnerview.chitchat.realtime.dto.ResumeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMessageControllerTest {

    private static final String TENANT = "tenant-1";
    private static final String CONVERSATION = "conv-1";
    private static final String USER = "user-1";

    @Mock
    private MessageService messageService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    private RealtimeMessageController controller;

    private final Principal principal = new UsernamePasswordAuthenticationToken(USER, null, List.of());

    @BeforeEach
    void setUp() {
        controller = new RealtimeMessageController(messageService, realtimeEventPublisher, 200);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void resumeReplaysMissedMessagesThenSignalsCompletion() {
        when(messageService.getMessages(CONVERSATION, null, 1042L, 200))
                .thenReturn(new MessagePageResponse(
                        List.of(message(1043L), message(1044L)), true, 1044L));
        when(messageService.getReadState(CONVERSATION))
                .thenReturn(new ReadStateResponse(CONVERSATION, 1042L, 1044L, 2));

        controller.resume(CONVERSATION, new ResumeRequest(1042L), accessor(), principal);

        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher, times(3)).publishToUser(eq(USER), captor.capture());

        List<RealtimeEvent> events = captor.getAllValues();
        assertThat(events.get(0).type()).isEqualTo(RealtimeEventType.MESSAGE_CREATED);
        assertThat(events.get(0).sequence()).isEqualTo(1043L);
        assertThat(events.get(1).type()).isEqualTo(RealtimeEventType.MESSAGE_CREATED);
        assertThat(events.get(1).sequence()).isEqualTo(1044L);
        assertThat(events.get(2).type()).isEqualTo(RealtimeEventType.SYNC_COMPLETE);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) events.get(2).payload();
        assertThat(payload.get("afterSequence")).isEqualTo(1042L);
        assertThat(payload.get("delivered")).isEqualTo(2);
        assertThat(payload.get("latestSequence")).isEqualTo(1044L);
        assertThat(payload.get("hasMore")).isEqualTo(true);
    }

    @Test
    void resumeWithoutPositionOnlyReportsTheLatestSequence() {
        when(messageService.getReadState(CONVERSATION))
                .thenReturn(new ReadStateResponse(CONVERSATION, 0L, 1050L, 1050));

        controller.resume(CONVERSATION, null, accessor(), principal);

        verify(messageService, never()).getMessages(anyString(), any(), any(), any());
        ArgumentCaptor<RealtimeEvent> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publishToUser(eq(USER), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(RealtimeEventType.SYNC_COMPLETE);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captor.getValue().payload();
        assertThat(payload.get("afterSequence")).isEqualTo(0L);
        assertThat(payload.get("delivered")).isEqualTo(0);
        assertThat(payload.get("latestSequence")).isEqualTo(1050L);
    }

    @Test
    void resumeWithoutTenantContextDoesNothing() {
        SimpMessageHeaderAccessor noTenant = SimpMessageHeaderAccessor.create();
        noTenant.setSessionAttributes(Map.of());

        controller.resume(CONVERSATION, new ResumeRequest(1L), noTenant, principal);

        verify(realtimeEventPublisher, never()).publishToUser(anyString(), any());
        verify(messageService, never()).getMessages(anyString(), any(), any(), any());
    }

    @Test
    void resumeClearsThreadLocalStateAfterwards() {
        when(messageService.getReadState(CONVERSATION))
                .thenReturn(new ReadStateResponse(CONVERSATION, 0L, 1L, 1));

        controller.resume(CONVERSATION, new ResumeRequest(0L), accessor(), principal);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void anonymousCallersAreIgnored() {
        controller.resume(CONVERSATION, new ResumeRequest(1L), accessor(), null);

        verify(realtimeEventPublisher, never()).publishToUser(anyString(), any());
        verify(messageService, never()).getMessages(anyString(), any(), any(), any());
    }

    private SimpMessageHeaderAccessor accessor() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(Map.of("tenantId", TENANT));
        return accessor;
    }

    private MessageResponse message(long sequence) {
        return new MessageResponse("m-" + sequence, CONVERSATION, TENANT, USER,
                "cmid-" + sequence, sequence, "content", null,
                LocalDateTime.now(), null, false, false);
    }
}
