import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { ChatMessage, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { SearchIcon } from '../components/icons';
import { useContextMenu } from '../hooks/useContextMenu';
import { formatShortDate } from '../lib/format';
import { copyText } from '../lib/ui';

interface SearchPanelProps {
  conversationId: string;
  profiles: Record<string, UserProfile>;
  onClose: () => void;
  onError: (err: unknown) => void;
  onJump: (conversationId: string, messageId: string) => void;
}

/** Header search: within the open conversation or across all of them. */
export default function SearchPanel({ conversationId, profiles, onClose, onError, onJump }: SearchPanelProps) {
  const [query, setQuery] = useState('');
  const [scope, setScope] = useState<'conversation' | 'everywhere'>('conversation');
  const [results, setResults] = useState<ChatMessage[]>([]);
  const [searching, setSearching] = useState(false);
  const { menu, openMenu, closeMenu } = useContextMenu<ChatMessage>();
  const inputRef = useRef<HTMLInputElement>(null);

  const hitMenuItems = (message: ChatMessage): ContextMenuItem[] => [
    {
      label: 'Copy text',
      onClick: () => copyText(message.content ?? ''),
    },
    {
      label: 'Copy message ID',
      onClick: () => copyText(message.id),
    },
    {
      label: 'Copy sender ID',
      onClick: () => copyText(message.senderId),
    },
  ];

  useEffect(() => inputRef.current?.focus(), []);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setResults([]);
      return;
    }
    setSearching(true);
    const handle = setTimeout(() => {
      const call =
        scope === 'conversation'
          ? api.searchInConversation(conversationId, trimmed)
          : api.searchAllMessages(trimmed);
      call
        .then(setResults)
        .catch(onError)
        .finally(() => setSearching(false));
    }, 300);
    return () => clearTimeout(handle);
  }, [query, scope, conversationId, onError]);

  return (
    <div className="search-panel">
      <div className="search-bar">
        <SearchIcon />
        <input
          ref={inputRef}
          value={query}
          onChange={event => setQuery(event.target.value)}
          placeholder="Search messages…"
          onKeyDown={event => event.key === 'Escape' && onClose()}
        />
        <div className="scope-toggle">
          <button
            className={scope === 'conversation' ? 'scope active' : 'scope'}
            onClick={() => setScope('conversation')}
          >
            This chat
          </button>
          <button
            className={scope === 'everywhere' ? 'scope active' : 'scope'}
            onClick={() => setScope('everywhere')}
          >
            Everywhere
          </button>
        </div>
        <button className="icon-btn" onClick={onClose} aria-label="Close search">
          ×
        </button>
      </div>

      {query.trim().length >= 2 && (
        <div className="search-results-panel">
          {searching && <p className="muted">Searching…</p>}
          {!searching && results.length === 0 && <p className="muted">No matches found.</p>}
          {results.map(message => (
            <div
              key={message.id}
              className="search-hit"
              role="button"
              onClick={() => onJump(message.conversationId, message.id)}
              onContextMenu={event => openMenu(event, message)}
            >
              <Avatar
                name={profiles[message.senderId]?.displayName ?? message.senderId}
                seed={message.senderId}
                size={26}
              />
              <div className="hit-text">
                <span className="hit-meta">
                  {profiles[message.senderId]?.displayName ?? message.senderId.slice(0, 8)}
                  {' · '}
                  {formatShortDate(message.createdAt)}
                </span>
                <span className="hit-content">
                  {message.deleted ? 'Message deleted' : message.content}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={hitMenuItems(menu.target)}
          onClose={closeMenu}
        />
      )}
    </div>
  );
}
