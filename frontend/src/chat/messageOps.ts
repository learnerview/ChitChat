import type { ChatMessage, MessagePage } from '../api/types';

/** Confirmed messages order by sequence; pending ones always sink to the end. */
function compare(a: ChatMessage, b: ChatMessage): number {
  const ap = a.status ? 1 : 0;
  const bp = b.status ? 1 : 0;
  if (ap !== bp) return ap - bp;
  if (ap === 1) return 0; // stable sort keeps send order among pending
  return a.sequence - b.sequence;
}

/**
 * Append a message, deduplicating by id (topic delivery and resume replay
 * overlap) and resolving optimistic sends: if the incoming message carries a
 * clientMessageId that matches a pending temporary message, the temporary is
 * replaced by the authoritative one — never shown twice.
 */
export function appendMessage(list: ChatMessage[], message: ChatMessage): ChatMessage[] {
  if (list.some(existing => existing.id === message.id)) {
    return list;
  }
  const resolved =
    message.clientMessageId != null
      ? list.filter(
          existing =>
            existing.status !== undefined &&
            existing.clientMessageId === message.clientMessageId,
        )
      : [];
  const base = resolved.length > 0 ? list.filter(m => !resolved.includes(m)) : list;
  return [...base, message].sort(compare);
}

/** Append a locally-created optimistic message (status: sending). */
export function pushLocal(list: ChatMessage[], message: ChatMessage): ChatMessage[] {
  return [...list, message].sort(compare);
}

/** Mark an optimistic message as failed (kept for retry). */
export function markSendFailed(list: ChatMessage[], messageId: string): ChatMessage[] {
  return list.map(message =>
    message.id === messageId ? { ...message, status: 'failed' } : message,
  );
}

/** Prepend an older history page, dropping anything already present. */
export function mergeOlderPage(current: ChatMessage[], page: MessagePage): ChatMessage[] {
  const known = new Set(current.map(message => message.id));
  const older = page.messages.filter(message => !known.has(message.id));
  return [...older, ...current];
}

/** Replace a message with its edited version (identity: same id). */
export function applyEdited(list: ChatMessage[], edited: ChatMessage): ChatMessage[] {
  return list.map(message => (message.id === edited.id ? edited : message));
}

/** Mark a message as deleted and strip its content. */
export function applyDeleted(list: ChatMessage[], messageId: string): ChatMessage[] {
  return list.map(message =>
    message.id === messageId ? { ...message, deleted: true, content: null } : message,
  );
}
