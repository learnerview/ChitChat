import { useEffect, useRef, useState } from 'react';
import type { ChatMessage, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { EditIcon, TrashIcon } from '../components/icons';
import { useSelection } from '../hooks/useSelection';
import { dayLabel, formatTime, sameDay } from '../lib/format';
import { copyText } from '../lib/ui';

const GROUP_GAP_MS = 5 * 60 * 1000;

interface MessageListProps {
  messages: ChatMessage[];
  profiles: Record<string, UserProfile>;
  myUserId: string;
  canModerate: boolean;
  hasMore: boolean;
  loading: boolean;
  seenMessageId: string | null;
  highlightId: string | null;
  onLoadOlder: () => void | Promise<void>;
  onReply: (message: ChatMessage) => void;
  onEdit: (message: ChatMessage) => void;
  onDelete: (message: ChatMessage) => void;
  onDeleteMessages: (ids: string[]) => Promise<void> | void;
  onRetry: (message: ChatMessage) => void;
  onSenderClick: (userId: string) => void;
}

interface MenuState {
  x: number;
  y: number;
  message: ChatMessage;
}

export default function MessageList({
  messages,
  profiles,
  myUserId,
  canModerate,
  hasMore,
  loading,
  seenMessageId,
  highlightId,
  onLoadOlder,
  onReply,
  onEdit,
  onDelete,
  onDeleteMessages,
  onRetry,
  onSenderClick,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const pendingRestore = useRef<number | null>(null);
  const [menu, setMenu] = useState<MenuState | null>(null);
  const { selectMode, selected, enterSelection, exitSelect, toggleSelected } = useSelection();
  const lastMessageId = messages.length > 0 ? messages[messages.length - 1].id : null;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 240;
    if (nearBottom) bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lastMessageId]);

  // Preserve scroll position when an older page is prepended (load older).
  useEffect(() => {
    const container = containerRef.current;
    if (!container || pendingRestore.current == null) return;
    const previous = pendingRestore.current;
    pendingRestore.current = null;
    container.scrollTop = container.scrollHeight - previous;
  }, [messages.length]);

  // Jump-to-message highlight (search results).
  useEffect(() => {
    if (!highlightId) return;
    const el = containerRef.current?.querySelector(`[data-message-id="${highlightId}"]`);
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [highlightId]);

  async function handleLoadOlder() {
    const container = containerRef.current;
    if (container) pendingRestore.current = container.scrollHeight - container.scrollTop;
    await onLoadOlder();
  }

  function openMenu(event: React.MouseEvent, message: ChatMessage) {
    if (selectMode) return;
    event.preventDefault();
    setMenu({ x: event.clientX, y: event.clientY, message });
  }

  function menuItems(message: ChatMessage): ContextMenuItem[] {
    const mine = message.senderId === myUserId;
    const items: ContextMenuItem[] = [];
    if (message.status === 'failed') {
      items.push({ label: 'Retry send', onClick: () => onRetry(message) });
      return items;
    }
    if (!message.deleted) {
      items.push({ label: 'Reply', onClick: () => onReply(message) });
      items.push({
        label: 'Copy text',
        onClick: () => copyText(message.content ?? ''),
      });
      items.push({
        label: 'Select',
        onClick: () => enterSelection(message.id),
      });
    }
    if (mine && !message.deleted) {
      items.push({ label: 'Edit', icon: <EditIcon />, onClick: () => onEdit(message) });
    }
    if ((mine || canModerate) && !message.deleted) {
      items.push({ label: 'Delete', icon: <TrashIcon />, danger: true, onClick: () => onDelete(message) });
    }
    return items;
  }

  const selectedMessages = messages.filter(m => selected.includes(m.id));
  const canBatchDelete =
    selectedMessages.length > 0 &&
    selectedMessages.every(m => !m.deleted && (m.senderId === myUserId || canModerate));

  async function batchDelete() {
    const ids = [...selected];
    exitSelect();
    await onDeleteMessages(ids);
  }

  function batchCopy() {
    const text = selectedMessages
      .filter(m => !m.deleted && m.content)
      .map(m => m.content)
      .join('\n');
    copyText(text);
    exitSelect();
  }

  const byId = new Map(messages.map(m => [m.id, m]));

  return (
    <div className="message-list" ref={containerRef}>
      {hasMore && (
        <button className="load-older" onClick={() => void handleLoadOlder()}>
          Load older messages
        </button>
      )}
      {loading && (
        <div className="skeleton-stack">
          {[0, 1, 2, 3, 4].map(i => (
            <div key={i} className={i % 2 ? 'skeleton-bubble right' : 'skeleton-bubble'} />
          ))}
        </div>
      )}
      {!loading &&
        messages.map((message, index) => {
          const previous = index > 0 ? messages[index - 1] : null;
          const dayBreak = !previous || !sameDay(previous.createdAt, message.createdAt);
          const grouped =
            !dayBreak &&
            previous !== null &&
            previous.senderId === message.senderId &&
            new Date(message.createdAt).getTime() - new Date(previous.createdAt).getTime() <
              GROUP_GAP_MS;
          const isSelected = selected.includes(message.id);

          return (
            <div key={`${message.id}-${message.deleted ? 'd' : ''}${message.edited ? 'e' : ''}`}>
              {dayBreak && <div className="date-separator">{dayLabel(message.createdAt)}</div>}
              <div
                data-message-id={message.id}
                className={[
                  'bubble-row',
                  message.senderId === myUserId ? 'mine' : '',
                  grouped ? 'grouped' : '',
                  isSelected ? 'selected' : '',
                  highlightId === message.id ? 'flash' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onContextMenu={event => openMenu(event, message)}
                onClick={() => {
                  if (selectMode) toggleSelected(message.id);
                }}
              >
                {selectMode && (
                  <span className={isSelected ? 'sel-check on' : 'sel-check'} aria-hidden>
                    ✓
                  </span>
                )}
                {!grouped && (
                  <Avatar
                    name={profiles[message.senderId]?.displayName ?? message.senderId}
                    seed={message.senderId}
                    size={34}
                  />
                )}
                <div className="bubble-stack">
                  {!grouped && (
                    <div className="bubble-meta">
                      <button
                        className="sender-link"
                        onClick={() => onSenderClick(message.senderId)}
                      >
                        {profiles[message.senderId]?.displayName ?? message.senderId.slice(0, 8)}
                      </button>
                      <span className="time">{formatTime(message.createdAt)}</span>
                    </div>
                  )}
                  <div className={message.deleted ? 'bubble deleted' : 'bubble'}>
                    {message.replyToId && !message.deleted && (
                      <div className="reply-quote">
                        <span className="reply-sender">
                          {profiles[byId.get(message.replyToId)?.senderId ?? '']?.displayName ??
                            'Reply'}
                        </span>
                        <span className="reply-snippet">
                          {byId.get(message.replyToId)?.deleted
                            ? 'Message deleted'
                            : (byId.get(message.replyToId)?.content ?? 'Original message')
                                .slice(0, 80)}
                        </span>
                      </div>
                    )}
                    {message.deleted ? (
                      <span className="deleted-text">This message was deleted</span>
                    ) : (
                      <span className="bubble-text">
                        {message.content}
                        {message.edited && <span className="edited"> (edited)</span>}
                      </span>
                    )}
                  </div>
                  {message.status === 'sending' && (
                    <div className="send-status sending">Sending…</div>
                  )}
                  {message.status === 'failed' && (
                    <div className="send-status failed">
                      <span>⚠ Failed to send</span>
                      <button
                        className="retry-link"
                        onClick={event => {
                          event.stopPropagation();
                          onRetry(message);
                        }}
                      >
                        Retry
                      </button>
                    </div>
                  )}
                  {message.id === seenMessageId && (
                    <div className="seen-indicator">✓✓ Seen by everyone</div>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      <div ref={bottomRef} />
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={menuItems(menu.message)}
          onClose={() => setMenu(null)}
        />
      )}

      {selectMode && (
        <div className="selection-bar">
          <span className="selection-count">{selected.length} selected</span>
          <button onClick={batchCopy} disabled={selected.length === 0}>
            Copy
          </button>
          {canBatchDelete && (
            <button className="danger" onClick={() => void batchDelete()}>
              Delete
            </button>
          )}
          <button onClick={exitSelect}>Cancel</button>
        </div>
      )}
    </div>
  );
}
