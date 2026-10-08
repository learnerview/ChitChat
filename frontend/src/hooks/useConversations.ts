import { useCallback, useState } from 'react';
import { api } from '../api/client';
import type { ChatMessage, Conversation, RealtimeEvent } from '../api/types';
import type { RealtimeClient } from '../ws/realtime';

export type ConversationEventHandler = (conversationId: string, event: RealtimeEvent) => void;

/**
 * Owns the conversation list: loading, realtime watch registration,
 * unread counters, last-message previews, and membership-refresh events.
 */
export function useConversations() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [lastMessages, setLastMessages] = useState<Record<string, ChatMessage>>({});

  const reset = useCallback(() => {
    setConversations([]);
    setLastMessages({});
  }, []);

  const noteLastMessage = useCallback((conversationId: string, message: ChatMessage) => {
    setLastMessages(prev => ({ ...prev, [conversationId]: message }));
  }, []);

  const watchAll = useCallback(
    (list: Conversation[], realtime: RealtimeClient | null, onEvent: ConversationEventHandler) => {
      if (!realtime) return;
      for (const conversation of list) {
        realtime.watchConversation(conversation.id, conversation.lastMessageSequence, event =>
          onEvent(conversation.id, event),
        );
      }
    },
    [],
  );

  const load = useCallback(
    async (realtime: RealtimeClient | null, onEvent: ConversationEventHandler) => {
      const list = await api.listConversations();
      setConversations(list);
      watchAll(list, realtime, onEvent);
      return list;
    },
    [watchAll],
  );

  const reload = useCallback(() => {
    void api.listConversations().then(setConversations).catch(() => undefined);
  }, []);

  const addConversation = useCallback(
    (conversation: Conversation, realtime: RealtimeClient | null, onEvent: ConversationEventHandler) => {
      setConversations(prev =>
        prev.some(c => c.id === conversation.id) ? prev : [conversation, ...prev],
      );
      realtime?.watchConversation(conversation.id, conversation.lastMessageSequence, event =>
        onEvent(conversation.id, event),
      );
    },
    [],
  );

  const markRead = useCallback((conversationId: string, latestSequence: number) => {
    setConversations(prev =>
      prev.map(c =>
        c.id === conversationId
          ? { ...c, unreadCount: 0, lastReadSequence: latestSequence }
          : c,
      ),
    );
  }, []);

  /** Optimistically applies viewer-scoped settings (pin/mute/archive) to the list. */
  const applySettings = useCallback(
    (conversationId: string, settings: Partial<Pick<Conversation, 'pinned' | 'muted' | 'archived' | 'notificationLevel'>>) => {
      setConversations(prev =>
        prev.map(c => (c.id === conversationId ? { ...c, ...settings } : c)),
      );
    },
    [],
  );

  const removeConversation = useCallback((conversationId: string) => {
    setConversations(prev => prev.filter(c => c.id !== conversationId));
    setLastMessages(prev => {
      if (!(conversationId in prev)) return prev;
      const next = { ...prev };
      delete next[conversationId];
      return next;
    });
  }, []);

  /** List-level effects of a realtime event (unread counts, previews, membership). */
  const handleListEvent = useCallback(
    (conversationId: string, event: RealtimeEvent, isActive: boolean) => {
      if (event.type === 'MESSAGE_CREATED') {
        const message = event.payload as ChatMessage;
        noteLastMessage(conversationId, message);
        setConversations(prev =>
          prev.map(c =>
            c.id === conversationId
              ? {
                  ...c,
                  lastMessageSequence: message.sequence,
                  lastMessageAt: message.createdAt,
                  unreadCount: isActive
                    ? 0
                    : Math.max(c.unreadCount, message.sequence - (c.lastReadSequence ?? 0)),
                }
              : c,
          ),
        );
      } else if (event.type === 'MESSAGE_EDITED') {
        const edited = event.payload as ChatMessage;
        setLastMessages(prev =>
          prev[conversationId]?.id === edited.id ? { ...prev, [conversationId]: edited } : prev,
        );
      } else if (
        event.type === 'MEMBER_ADDED' ||
        event.type === 'MEMBER_REMOVED' ||
        event.type === 'MEMBER_ROLE_CHANGED'
      ) {
        reload();
      } else if (event.type === 'CONVERSATION_DELETED') {
        removeConversation(conversationId);
      }
    },
    [noteLastMessage, reload, removeConversation],
  );

  return {
    conversations,
    lastMessages,
    reset,
    noteLastMessage,
    load,
    reload,
    addConversation,
    markRead,
    applySettings,
    removeConversation,
    handleListEvent,
  };
}
