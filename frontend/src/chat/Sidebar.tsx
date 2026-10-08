import { useEffect, useRef, useState } from 'react';
import type { AuthResponse, ChatMessage, Conversation, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { ArchiveIcon, LogoutIcon, MuteIcon, PinIcon, PlusIcon, TrashIcon, UsersIcon } from '../components/icons';
import { useSelection } from '../hooks/useSelection';
import { conversationTitle } from '../lib/conversations';
import { relativeTime } from '../lib/format';

export interface ConversationSettingsPatch {
  pinned?: boolean;
  muted?: boolean;
  archived?: boolean;
}

interface SidebarProps {
  auth: AuthResponse;
  conversations: Conversation[];
  profiles: Record<string, UserProfile>;
  lastMessages: Record<string, ChatMessage>;
  activeId: string | null;
  status: 'connected' | 'connecting' | 'offline';
  onOpenProfile: () => void;
  onSelect: (conversationId: string) => void;
  onNewDm: () => void;
  onNewGroup: () => void;
  onSwitchWorkspace: (tenantId: string) => void;
  onCreateWorkspace: () => void;
  onJoinWorkspace: () => void;
  onWorkspaceSettings: () => void;
  onUpdateSettings: (conversation: Conversation, patch: ConversationSettingsPatch) => void;
  onDeleteConversation: (conversation: Conversation) => void;
  onDeleteConversations: (conversations: Conversation[]) => void;
  onLogout: () => void;
}

export default function Sidebar({
  auth,
  conversations,
  profiles,
  lastMessages,
  activeId,
  status,
  onOpenProfile,
  onSelect,
  onNewDm,
  onNewGroup,
  onSwitchWorkspace,
  onCreateWorkspace,
  onJoinWorkspace,
  onWorkspaceSettings,
  onUpdateSettings,
  onDeleteConversation,
  onDeleteConversations,
  onLogout,
}: SidebarProps) {
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false);
  const [menu, setMenu] = useState<{ x: number; y: number; conversation: Conversation } | null>(null);
  const [wsMenu, setWsMenu] = useState<{ x: number; y: number } | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [leavingId, setLeavingId] = useState<string | null>(null);
  const { selectMode, selected, enterSelection, exitSelect, toggleSelected } = useSelection();
  const leavingTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (!workspaceMenuOpen) return;
    const close = () => setWorkspaceMenuOpen(false);
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [workspaceMenuOpen]);

  const selectedConversations = conversations.filter(c => selected.includes(c.id));

  useEffect(() => () => window.clearTimeout(leavingTimer.current), []);

  /** Plays the exit animation, then commits the change so the row can unmount. */
  const animateOut = (conversation: Conversation, commit: () => void) => {
    window.clearTimeout(leavingTimer.current);
    setLeavingId(conversation.id);
    leavingTimer.current = window.setTimeout(() => {
      setLeavingId(null);
      commit();
    }, 280);
  };

  const menuItems = (conversation: Conversation): ContextMenuItem[] => {
    const items: ContextMenuItem[] = [
      {
        label: 'Select',
        onClick: () => enterSelection(conversation.id),
      },
      {
        label: conversation.pinned ? 'Unpin' : 'Pin to top',
        icon: <PinIcon />,
        onClick: () => onUpdateSettings(conversation, { pinned: !conversation.pinned }),
      },
      {
        label: conversation.muted ? 'Unmute' : 'Mute',
        icon: <MuteIcon />,
        onClick: () => onUpdateSettings(conversation, { muted: !conversation.muted }),
      },
      {
        label: conversation.archived ? 'Unarchive' : 'Archive',
        icon: <ArchiveIcon />,
        onClick: () => {
          if (!conversation.archived) {
            animateOut(conversation, () =>
              onUpdateSettings(conversation, { archived: true }),
            );
          } else {
            onUpdateSettings(conversation, { archived: false });
          }
        },
      },
    ];
    items.push({
      label: 'Delete conversation',
      icon: <TrashIcon />,
      danger: true,
      onClick: () => onDeleteConversation(conversation),
    });
    return items;
  };

  const visible = conversations.filter(c => showArchived || !c.archived);
  const activeOnes = visible.filter(c => !c.pinned);
  const pinned = visible.filter(c => c.pinned);
  const sorted = [...pinned, ...activeOnes];
  const archivedCount = conversations.filter(c => c.archived).length;

  const renderRow = (conversation: Conversation, index: number) => {
    const last = lastMessages[conversation.id];
    const isSelected = selected.includes(conversation.id);
    return (
      <button
        key={conversation.id}
        className={[
          'conversation',
          conversation.id === activeId ? 'active' : '',
          conversation.muted ? 'muted-conv' : '',
          leavingId === conversation.id ? 'leaving' : '',
          isSelected ? 'selected' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        style={{ animationDelay: `${Math.min(index, 12) * 35}ms` }}
        onClick={() => {
          if (selectMode) toggleSelected(conversation.id);
          else onSelect(conversation.id);
        }}
        onContextMenu={event => {
          event.preventDefault();
          if (selectMode) {
            toggleSelected(conversation.id);
            return;
          }
          setMenu({ x: event.clientX, y: event.clientY, conversation });
        }}
      >
        {selectMode && (
          <span className={isSelected ? 'sel-check on' : 'sel-check'} aria-hidden>
            ✓
          </span>
        )}
        <Avatar
          name={conversationTitle(conversation, profiles, auth.userId)}
          seed={conversation.id}
          size={38}
        />
        <div className="conversation-text">
          <div className="conversation-top">
            <span className="conversation-name">
              {conversation.pinned && <PinIcon size={11} />}
              {conversationTitle(conversation, profiles, auth.userId)}
            </span>
            {conversation.lastMessageAt && (
              <span className="conversation-time">{relativeTime(conversation.lastMessageAt)}</span>
            )}
          </div>
          <div className="conversation-bottom">
            <span className="conversation-preview">
              {last
                ? last.deleted
                  ? 'Message deleted'
                  : (last.content ?? '').slice(0, 42)
                : conversation.type === 'GROUP'
                  ? 'Group conversation'
                  : 'Direct message'}
            </span>
            {conversation.muted && <span className="muted-dot" title="Muted">●</span>}
            {conversation.unreadCount > 0 && !conversation.muted && (
              <span className="badge">{conversation.unreadCount}</span>
            )}
            {conversation.unreadCount > 0 && conversation.muted && (
              <span className="badge muted-badge">{conversation.unreadCount}</span>
            )}
          </div>
        </div>
      </button>
    );
  };

  return (
    <aside className="sidebar">
      <div
        className="workspace-card"
        onMouseDown={event => event.stopPropagation()}
        onClick={() => setWorkspaceMenuOpen(open => !open)}
        onContextMenu={event => {
          event.preventDefault();
          setWsMenu({ x: event.clientX, y: event.clientY });
        }}
      >
        <div className="workspace-avatar">{auth.currentTenantName.slice(0, 2).toUpperCase()}</div>
        <div className="workspace-info">
          <strong>{auth.currentTenantName}</strong>
          <span className={`ws-status ${status === 'connected' ? 'online' : ''}`}>
            {status === 'connected'
              ? 'Connected'
              : status === 'connecting'
                ? 'Reconnecting…'
                : 'Offline'}
          </span>
        </div>
        <span className={`chevron ${workspaceMenuOpen ? 'open' : ''}`}>▾</span>
      </div>

      {workspaceMenuOpen && (
        <div className="workspace-menu menu-pop" onMouseDown={event => event.stopPropagation()}>
          {auth.tenants
            .filter(tenant => tenant.id !== auth.currentTenantId)
            .map((tenant, index) => (
              <button
                key={tenant.id}
                style={{ animationDelay: `${index * 30}ms` }}
                onClick={() => {
                  setWorkspaceMenuOpen(false);
                  onSwitchWorkspace(tenant.id);
                }}
              >
                <span className="workspace-avatar small">{tenant.name.slice(0, 2).toUpperCase()}</span>
                {tenant.name}
                <span className={`role-badge ${tenant.role.toLowerCase()}`}>{tenant.role}</span>
              </button>
            ))}
          <hr />
          <button onClick={() => { setWorkspaceMenuOpen(false); onWorkspaceSettings(); }}>
            <UsersIcon /> Workspace settings
          </button>
          <button onClick={() => { setWorkspaceMenuOpen(false); onCreateWorkspace(); }}>
            <PlusIcon /> Create workspace
          </button>
          <button onClick={() => { setWorkspaceMenuOpen(false); onJoinWorkspace(); }}>
            <PlusIcon /> Join with invite
          </button>
        </div>
      )}

      <div className="sidebar-actions">
        <button className="action-btn" onClick={onNewDm}>
          <PlusIcon /> Direct message
        </button>
        <button className="action-btn" onClick={onNewGroup}>
          <PlusIcon /> Group
        </button>
      </div>

      <nav className="conversation-list">
        {sorted.map(renderRow)}
        {sorted.length === 0 && !showArchived && (
          <p className="muted empty-list">No conversations yet — start one above.</p>
        )}
        {archivedCount > 0 && (
          <button className="archived-toggle" onClick={() => setShowArchived(open => !open)}>
            <ArchiveIcon /> {showArchived ? 'Hide' : 'Show'} archived ({archivedCount})
          </button>
        )}
        {showArchived && conversations.filter(c => c.archived).map(c => renderRow(c, 99))}
      </nav>

      {selectMode && (
        <div className="selection-bar">
          <span className="selection-count">{selected.length} selected</span>
          <button
            disabled={selected.length === 0}
            onClick={() => {
              selectedConversations.forEach(c =>
                onUpdateSettings(c, { pinned: true }),
              );
              exitSelect();
            }}
          >
            Pin
          </button>
          <button
            disabled={selected.length === 0}
            onClick={() => {
              selectedConversations.forEach(c =>
                onUpdateSettings(c, { archived: !c.archived }),
              );
              exitSelect();
            }}
          >
            Archive
          </button>
          <button
            className="danger"
            disabled={selected.length === 0}
            onClick={() => {
              const chosen = selectedConversations;
              exitSelect();
              onDeleteConversations(chosen);
            }}
          >
            Delete
          </button>
          <button onClick={exitSelect}>Cancel</button>
        </div>
      )}

      <div className="sidebar-bottom">
        <button className="me" onClick={onOpenProfile} title="My profile">
          <Avatar name={auth.displayName} seed={auth.userId} size={34} />
          <div className="me-info">
            <strong>{auth.displayName}</strong>
            <span className="muted">@{auth.username}</span>
          </div>
        </button>
        <button className="icon-btn" title="Sign out" onClick={onLogout}>
          <LogoutIcon />
        </button>
      </div>

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={menuItems(menu.conversation)}
          onClose={() => setMenu(null)}
        />
      )}
      {wsMenu && (
        <ContextMenu
          x={wsMenu.x}
          y={wsMenu.y}
          onClose={() => setWsMenu(null)}
          items={[
            ...auth.tenants.map(tenant => ({
              label: `Switch to ${tenant.name}`,
              onClick: () => onSwitchWorkspace(tenant.id),
            })),
            {
              label: 'Workspace settings',
              icon: <UsersIcon />,
              onClick: onWorkspaceSettings,
            },
            {
              label: 'Create workspace',
              icon: <PlusIcon />,
              onClick: onCreateWorkspace,
            },
            {
              label: 'Join with invite',
              icon: <PlusIcon />,
              onClick: onJoinWorkspace,
            },
          ]}
        />
      )}
    </aside>
  );
}
