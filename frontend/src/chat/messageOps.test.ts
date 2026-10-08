import { describe, expect, it } from 'vitest';
import type { ChatMessage, MessagePage } from '../api/types';
import { appendMessage, applyDeleted, applyEdited, markSendFailed, mergeOlderPage, pushLocal } from './messageOps';

function message(id: string, sequence: number, content = 'hi'): ChatMessage {
  return {
    id,
    conversationId: 'c1',
    tenantId: 't1',
    senderId: 'u1',
    clientMessageId: null,
    sequence,
    content,
    replyToId: null,
    createdAt: '2026-10-08T09:00:00',
    editedAt: null,
    edited: false,
    deleted: false,
  };
}

function pending(id: string, clientMessageId: string, content = 'optimistic'): ChatMessage {
  return {
    ...message(id, 0, content),
    clientMessageId,
    status: 'sending',
  };
}

describe('appendMessage', () => {
  it('appends a new message in sequence order', () => {
    const list = [message('m1', 1), message('m3', 3)];
    const result = appendMessage(list, message('m2', 2));
    expect(result.map(m => m.id)).toEqual(['m1', 'm2', 'm3']);
  });

  it('deduplicates by id (topic delivery overlapping resume replay)', () => {
    const list = [message('m1', 1)];
    const result = appendMessage(list, message('m1', 1));
    expect(result).toBe(list);
    expect(result).toHaveLength(1);
  });
});

describe('mergeOlderPage', () => {
  it('prepends older messages without duplicating overlap', () => {
    const current = [message('m3', 3), message('m4', 4)];
    const page: MessagePage = {
      messages: [message('m1', 1), message('m2', 2), message('m3', 3)],
      hasMore: false,
      nextCursor: null,
    };
    const result = mergeOlderPage(current, page);
    expect(result.map(m => m.id)).toEqual(['m1', 'm2', 'm3', 'm4']);
  });
});

describe('applyEdited', () => {
  it('replaces the message with the same id only', () => {
    const list = [message('m1', 1), message('m2', 2)];
    const edited = { ...message('m2', 2, 'new text'), edited: true };
    const result = applyEdited(list, edited);
    expect(result[1].content).toBe('new text');
    expect(result[1].edited).toBe(true);
    expect(result[0].content).toBe('hi');
  });
});

describe('applyDeleted', () => {
  it('marks the message deleted and strips content', () => {
    const list = [message('m1', 1), message('m2', 2)];
    const result = applyDeleted(list, 'm1');
    expect(result[0].deleted).toBe(true);
    expect(result[0].content).toBeNull();
    expect(result[1].deleted).toBe(false);
  });
});

describe('optimistic sends', () => {
  it('pushLocal appends a pending message that sinks below confirmed ones', () => {
    const list = [message('m1', 1), message('m2', 2)];
    const result = pushLocal(list, pending('tmp-1', 'cm-1'));
    expect(result.map(m => m.id)).toEqual(['m1', 'm2', 'tmp-1']);
    expect(result[2].status).toBe('sending');
  });

  it('keeps pending messages in send order (stable) after newer confirmed arrive', () => {
    let list = pushLocal([], pending('tmp-1', 'cm-1'));
    list = pushLocal(list, pending('tmp-2', 'cm-2'));
    list = appendMessage(list, message('m9', 9));
    expect(list.map(m => m.id)).toEqual(['m9', 'tmp-1', 'tmp-2']);
  });

  it('resolves the pending temp message when the REST echo returns', () => {
    let list = pushLocal([], pending('tmp-1', 'cm-1'));
    const confirmed = { ...message('srv-1', 3), clientMessageId: 'cm-1' };
    list = appendMessage(list, confirmed);
    expect(list.map(m => m.id)).toEqual(['srv-1']);
    expect(list[0].status).toBeUndefined();
  });

  it('resolves the pending temp message when realtime echoes it', () => {
    let list = pushLocal([], pending('tmp-1', 'cm-1'));
    list = appendMessage(list, message('m1', 1));
    const echoed = { ...message('srv-1', 4), clientMessageId: 'cm-1' };
    list = appendMessage(list, echoed);
    expect(list.map(m => m.id)).toEqual(['m1', 'srv-1']);
  });

  it('does not resolve a temp message with a different clientMessageId', () => {
    let list = pushLocal([], pending('tmp-1', 'cm-1'));
    list = appendMessage(list, { ...message('srv-2', 5), clientMessageId: 'cm-other' });
    expect(list.map(m => m.id)).toEqual(['srv-2', 'tmp-1']);
  });

  it('markSendFailed flags the temp message for retry', () => {
    let list = pushLocal([], pending('tmp-1', 'cm-1'));
    list = markSendFailed(list, 'tmp-1');
    expect(list[0].status).toBe('failed');
  });
});
