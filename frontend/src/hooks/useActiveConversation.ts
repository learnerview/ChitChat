import { useCallback, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { ChatMessage, Conversation, RealtimeEvent, UserProfile } from '../api/types';
import type { RealtimeClient } from '../ws/realtime';
import {
  appendMessage,
  applyDeleted,
  applyEdited,
  markSendFailed,
  mergeOlderPage,
  pushLocal,
} from '../chat/messageOps';

interface ActiveConversationDeps {
  onError: (err: unknown) => void;
  onProfiles: (users: UserProfile[]) => void;
  /** Called whenever a message becomes visible (list preview + unread reset). */
  onMessageSeen: (conversationId: string, message: ChatMessage) => void;
  /** Called when the read cursor advances (badge reset in the list). */
  onReadMarked: (conversationId: string, latestSequence: number) => void;
  /** Current user id — used to stamp optimistic messages. */
  myUserId: string;
  /** Current workspace id — stamped on optimistic messages. */
  myTenantId: string;
}

/**
 * Owns the currently open conversation: history loading + pagination,
 * incoming-event application, send/edit/delete mutations, and composer state
 * (editing / replying).
 */
export function useActiveConversation({
  onError,
  onProfiles,
  onMessageSeen,
  onReadMarked,
  myUserId,
  myTenantId,
}: ActiveConversationDeps) {
  const [activeId, setActiveId] = useState<string | null>(null);
  const [detail, setDetail] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<ChatMessage | null>(null);
  const [replyingTo, setReplyingTo] = useState<ChatMessage | null>(null);

  const activeIdRef = useRef<string | null>(null);
  activeIdRef.current = activeId;
  const messagesRef = useRef<ChatMessage[]>([]);
  messagesRef.current = messages;
  const cursorRef = useRef<number | null>(null);
  cursorRef.current = nextCursor;
  const hasMoreRef = useRef(false);
  hasMoreRef.current = hasMore;

  const isActive = useCallback((conversationId: string) => activeIdRef.current === conversationId, []);

  const close = useCallback(() => {
    setActiveId(null);
    setDetail(null);
    setMessages([]);
    setEditing(null);
    setReplyingTo(null);
  }, []);

  const open = useCallback(
    async (conversationId: string, realtime: RealtimeClient | null) => {
      setActiveId(conversationId);
      setMessages([]);
      setEditing(null);
      setReplyingTo(null);
      setLoading(true);
      try {
        const [page, conversation] = await Promise.all([
          api.getMessages(conversationId, null, 30),
          api.getConversation(conversationId),
        ]);
        setMessages(page.messages);
        setHasMore(page.hasMore);
        setNextCursor(page.nextCursor);
        setDetail(conversation);

        const latest = page.messages.length > 0 ? page.messages[page.messages.length - 1] : null;
        if (latest) {
          realtime?.noteSequence(conversationId, latest.sequence);
          onMessageSeen(conversationId, latest);
          void api.markRead(conversationId, latest.sequence).catch(() => undefined);
          onReadMarked(conversationId, latest.sequence);
        }
        if (conversation.members.length > 0) {
          void api
            .getProfiles(conversation.members.map(m => m.userId))
            .then(onProfiles)
            .catch(() => undefined);
        }
      } catch (err) {
        onError(err);
      } finally {
        setLoading(false);
      }
    },
    [onError, onProfiles, onMessageSeen, onReadMarked],
  );

  const loadOlder = useCallback(async () => {
    if (!activeId || nextCursor == null) return;
    try {
      const page = await api.getMessages(activeId, nextCursor, 30);
      setMessages(prev => mergeOlderPage(prev, page));
      setHasMore(page.hasMore);
      setNextCursor(page.nextCursor);
    } catch (err) {
      onError(err);
    }
  }, [activeId, nextCursor, onError]);

  /**
   * Page backwards until the target message is loaded (search-result jump).
   * Returns true when the message is present in the list.
   */
  const jumpToMessage = useCallback(
    async (messageId: string): Promise<boolean> => {
      const convId = activeIdRef.current;
      if (!convId) return false;
      const known = () => messagesRef.current.some(m => m.id === messageId);
      if (known()) return true;
      for (let page = 0; page < 50; page += 1) {
        const cursor = cursorRef.current;
        if (cursor == null || !hasMoreRef.current) break;
        try {
          const result = await api.getMessages(convId, cursor, 30);
          setMessages(prev => mergeOlderPage(prev, result));
          setHasMore(result.hasMore);
          setNextCursor(result.nextCursor);
          if (result.messages.some(m => m.id === messageId)) return true;
        } catch (err) {
          onError(err);
          return false;
        }
      }
      // Latest page may already contain it (state settled after open()).
      await new Promise(resolve => setTimeout(resolve, 50));
      return known();
    },
    [onError],
  );

  /** Apply a realtime event (topic or sync replay) to the open conversation. */
  const handleMessageEvent = useCallback(
    (event: RealtimeEvent) => {
      if (event.conversationId !== activeIdRef.current) return;
      if (event.type === 'MESSAGE_CREATED') {
        const message = event.payload as ChatMessage;
        setMessages(prev => appendMessage(prev, message));
        onMessageSeen(event.conversationId, message);
        void api.markRead(event.conversationId, message.sequence).catch(() => undefined);
        onReadMarked(event.conversationId, message.sequence);
      } else if (event.type === 'MESSAGE_EDITED') {
        setMessages(prev => applyEdited(prev, event.payload as ChatMessage));
      } else if (event.type === 'MESSAGE_DELETED') {
        const payload = event.payload as { messageId: string };
        setMessages(prev => applyDeleted(prev, payload.messageId));
      } else if (event.type === 'READ_UPDATED') {
        // Live read receipts: advance the member's cursor so "Seen" updates.
        const payload = event.payload as { userId: string; lastReadSequence: number };
        setDetail(prev =>
          prev
            ? {
                ...prev,
                members: prev.members.map(m =>
                  m.userId === payload.userId
                    ? { ...m, lastReadSequence: Math.max(m.lastReadSequence, payload.lastReadSequence) }
                    : m,
                ),
              }
            : prev,
        );
      } else if (
        event.type === 'MEMBER_ADDED' ||
        event.type === 'MEMBER_REMOVED' ||
        event.type === 'MEMBER_ROLE_CHANGED'
      ) {
        void api.getConversation(event.conversationId).then(setDetail).catch(() => undefined);
      }
    },
    [onMessageSeen, onReadMarked],
  );

  const send = useCallback(
    async (content: string) => {
      if (!activeIdRef.current) return;
      if (editing) {
        try {
          const saved = await api.editMessage(editing.id, content);
          setMessages(prev => applyEdited(prev, saved));
          setEditing(null);
        } catch (err) {
          onError(err);
        }
        return;
      }

      // Optimistic send: show the message immediately, resolve it via the
      // clientMessageId match when the REST response (or the realtime echo)
      // arrives, or mark it failed for retry.
      const conversationId = activeIdRef.current;
      const clientMessageId = crypto.randomUUID();
      const temp: ChatMessage = {
        id: `temp-${clientMessageId}`,
        conversationId,
        tenantId: myTenantId,
        senderId: myUserId,
        clientMessageId,
        sequence: 0,
        content,
        replyToId: replyingTo?.id ?? null,
        createdAt: new Date().toISOString(),
        editedAt: null,
        edited: false,
        deleted: false,
        status: 'sending',
      };
      setMessages(prev => pushLocal(prev, temp));
      setReplyingTo(null);
      try {
        const saved = await api.sendMessage(
          conversationId,
          content,
          clientMessageId,
          temp.replyToId,
        );
        setMessages(prev => appendMessage(prev, saved));
        onMessageSeen(saved.conversationId, saved);
      } catch (err) {
        setMessages(prev => markSendFailed(prev, temp.id));
        if (err instanceof ApiError && err.status === 401) onError(err);
        // other failures surface inline on the bubble (⚠ Retry) — no toast spam
      }
    },
    [editing, replyingTo, onError, onMessageSeen, myUserId, myTenantId],
  );

  /** Re-send a failed optimistic message. The same clientMessageId keeps it idempotent. */
  const retrySend = useCallback(
    async (message: ChatMessage) => {
      if (!activeIdRef.current || !message.clientMessageId) return;
      setMessages(prev =>
        prev.map(m => (m.id === message.id ? { ...m, status: 'sending' as const } : m)),
      );
      try {
        const saved = await api.sendMessage(
          message.conversationId,
          message.content ?? '',
          message.clientMessageId,
          message.replyToId,
        );
        setMessages(prev => appendMessage(prev, saved));
        onMessageSeen(saved.conversationId, saved);
      } catch (err) {
        setMessages(prev => markSendFailed(prev, message.id));
        if (err instanceof ApiError && err.status === 401) onError(err);
      }
    },
    [onError, onMessageSeen],
  );

  /** Batch delete: every id is attempted; failures surface through onError. */
  const deleteMessages = useCallback(
    async (ids: string[]) => {
      const results = await Promise.allSettled(ids.map(id => api.deleteMessage(id)));
      let failed = 0;
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          setMessages(prev => applyDeleted(prev, ids[index]));
        } else {
          failed += 1;
        }
      });
      if (failed > 0) {
        onError(new Error(`Failed to delete ${failed} message${failed > 1 ? 's' : ''}`));
      }
    },
    [onError],
  );

  return {
    activeId,
    detail,
    setDetail,
    messages,
    hasMore,
    loading,
    editing,
    replyingTo,
    isActive,
    open,
    close,
    loadOlder,
    jumpToMessage,
    handleMessageEvent,
    send,
    retrySend,
    deleteMessages,
    selectEdit: setEditing,
    selectReply: setReplyingTo,
    cancelEdit: () => setEditing(null),
    cancelReply: () => setReplyingTo(null),
  };
}
