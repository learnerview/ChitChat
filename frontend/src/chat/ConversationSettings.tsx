import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Conversation, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import Dialog from '../components/Dialog';
import { CloseIcon, TransferIcon } from '../components/icons';
import { useContextMenu } from '../hooks/useContextMenu';
import { copyText, errorMessage } from '../lib/ui';

interface ConversationSettingsProps {
  conversation: Conversation;
  myUserId: string;
  profiles: Record<string, UserProfile>;
  onProfilesLoaded: (users: UserProfile[]) => void;
  onClose: () => void;
  onUpdated: (conversation: Conversation) => void;
  onLeft: () => void;
  onDelete?: () => void;
  onViewProfile?: (userId: string) => void;
  pushSuccess: (message: string) => void;
}

export default function ConversationSettings({
  conversation,
  myUserId,
  profiles,
  onProfilesLoaded,
  onClose,
  onUpdated,
  onLeft,
  onDelete,
  onViewProfile,
  pushSuccess,
}: ConversationSettingsProps) {
  const isGroup = conversation.type === 'GROUP';
  const myRole = conversation.members.find(m => m.userId === myUserId)?.role ?? 'MEMBER';
  const isOwner = myRole === 'OWNER';

  const [name, setName] = useState(conversation.name ?? '');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserProfile[]>([]);
  const [error, setError] = useState<string | null>(null);
  const { menu, openMenu, closeMenu } = useContextMenu<string>();

  useEffect(() => {
    const missing = conversation.members.map(m => m.userId).filter(id => !profiles[id]);
    if (missing.length > 0) {
      void api.getProfiles(missing).then(onProfilesLoaded).catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.id]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const handle = setTimeout(() => {
      api
        .searchUsers(query.trim())
        .then(users =>
          setResults(users.filter(u => !conversation.members.some(m => m.userId === u.id && !m.leftAt))),
        )
        .catch(() => setResults([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [query, conversation.members]);

  const run = useCallback(
    async (action: () => Promise<Conversation>, successMessage?: string) => {
      try {
        onUpdated(await action());
        setError(null);
        if (successMessage) pushSuccess(successMessage);
      } catch (err) {
        setError(errorMessage(err, 'Action failed'));
      }
    },
    [onUpdated, pushSuccess],
  );

  const activeMembers = conversation.members.filter(m => !m.leftAt);

  const memberMenuItems = (userId: string): ContextMenuItem[] => {
    const items: ContextMenuItem[] = [
      {
        label: 'View profile',
        onClick: () => onViewProfile?.(userId),
      },
      {
        label: 'Copy user ID',
        onClick: () => copyText(userId),
      },
    ];
    if (isOwner && userId !== myUserId) {
      items.push(
        {
          label: 'Transfer ownership',
          icon: <TransferIcon />,
          onClick: () => void run(() => api.transferOwnership(conversation.id, userId), 'Ownership transferred'),
        },
        {
          label: 'Remove from conversation',
          icon: <CloseIcon />,
          danger: true,
          onClick: () => void run(() => api.removeConversationMember(conversation.id, userId), 'Member removed'),
        },
      );
    }
    return items;
  };

  return (
    <>
      <Dialog onClose={onClose} className="wide">
        <h3>{isGroup ? 'Group settings' : 'Conversation details'}</h3>

        {isGroup && isOwner && (
          <section className="settings-section">
            <h4>Name</h4>
            <div className="inline-row">
              <input value={name} onChange={event => setName(event.target.value)} />
              <button
                className="primary"
                disabled={!name.trim() || name.trim() === conversation.name}
                onClick={() => void run(() => api.renameConversation(conversation.id, name.trim()), 'Conversation renamed')}
              >
                Rename
              </button>
            </div>
          </section>
        )}

        <section className="settings-section">
          <h4>Members ({activeMembers.length})</h4>
          <div className="member-list">
            {activeMembers.map(member => {
              const profile = profiles[member.userId];
              return (
                <div
                  key={member.userId}
                  className="member-row"
                  onContextMenu={event => openMenu(event, member.userId)}
                >
                  <Avatar
                    name={profile?.displayName ?? member.userId}
                    seed={member.userId}
                    size={32}
                  />
                  <div className="member-info">
                    <strong>{profile?.displayName ?? member.userId.slice(0, 8)}</strong>
                    <span className="muted">{profile?.username ? `@${profile.username}` : member.userId.slice(0, 8)}</span>
                  </div>
                  <span className={`role-badge ${member.role.toLowerCase()}`}>{member.role}</span>
                  {isOwner && member.userId !== myUserId && (
                    <div className="member-actions">
                      <button
                        className="icon-btn"
                        title="Transfer ownership"
                        onClick={() =>
                          void run(() => api.transferOwnership(conversation.id, member.userId), 'Ownership transferred')
                        }
                      >
                        <TransferIcon />
                      </button>
                      <button
                        className="icon-btn danger-hover"
                        title="Remove member"
                        onClick={() =>
                          void run(() => api.removeConversationMember(conversation.id, member.userId), 'Member removed')
                        }
                      >
                        <CloseIcon />
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>

        {isGroup && isOwner && (
          <section className="settings-section">
            <h4>Add member</h4>
            <input
              value={query}
              onChange={event => setQuery(event.target.value)}
              placeholder="Search people in this workspace"
            />
            <div className="search-results">
              {results.map(user => (
                <button
                  key={user.id}
                  className="search-result"
                  onClick={() => {
                    setQuery('');
                    setResults([]);
                    void run(() => api.addConversationMember(conversation.id, user.id), 'Member added');
                  }}
                >
                  <Avatar name={user.displayName} seed={user.id} size={28} />
                  <strong>{user.displayName}</strong>
                  <span className="muted">@{user.username}</span>
                </button>
              ))}
            </div>
          </section>
        )}

        {error && <p className="error">{error}</p>}

        <div className="dialog-actions space-between">
          {isGroup && isOwner && onDelete ? (
            <button className="danger" onClick={onDelete}>
              Delete conversation
            </button>
          ) : (
            <button
              className="danger"
              onClick={() => {
                void api
                  .leaveConversation(conversation.id)
                  .then(onLeft)
                  .catch(err => setError(errorMessage(err, 'Failed to leave')));
              }}
            >
              Leave conversation
            </button>
          )}
          <button onClick={onClose}>Close</button>
        </div>
      </Dialog>

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={memberMenuItems(menu.target)}
          onClose={closeMenu}
        />
      )}
    </>
  );
}
