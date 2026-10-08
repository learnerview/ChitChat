import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '../api/client';
import type { Conversation, RealtimeEvent, UserProfile } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useProfiles } from '../hooks/useProfiles';
import { useConversations } from '../hooks/useConversations';
import { useActiveConversation } from '../hooks/useActiveConversation';
import { useRealtimeConnection } from '../hooks/useRealtimeConnection';
import { ToastStack, useToasts } from '../components/Toasts';
import Avatar from '../components/Avatar';
import { ChatIcon, GearIcon, RefreshIcon, SearchIcon } from '../components/icons';
import Sidebar, { type ConversationSettingsPatch } from './Sidebar';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import NewConversation from './NewConversation';
import ConversationSettings from './ConversationSettings';
import SearchPanel from './SearchPanel';
import WorkspaceDialog from '../workspace/WorkspaceDialog';
import WorkspaceSettings from '../workspace/WorkspaceSettings';
import ProfileDialog from '../user/ProfileDialog';
import UserProfileCard from '../user/UserProfileCard';
import ConfirmDialog from '../components/ConfirmDialog';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { conversationTitle, dmPeerId } from '../lib/conversations';
import { copyText, errorMessage } from '../lib/ui';

type Dialog =
  | 'dm'
  | 'group'
  | 'create-ws'
  | 'join-ws'
  | 'ws-settings'
  | 'conv-settings'
  | 'profile'
  | null;

export default function ChatPage() {
  const { auth, logout, switchWorkspace } = useAuth();
  const toasts = useToasts();
  const [dialog, setDialog] = useState<Dialog>(null);
  const [joinToken, setJoinToken] = useState('');

  // Support join deep links: /invite/<token> → open the join dialog prefilled.
  useEffect(() => {
    const match = window.location.pathname.match(/\/invite\/([A-Za-z0-9_-]+)/);
    if (match) {
      setJoinToken(match[1]);
      setDialog('join-ws');
      window.history.replaceState(null, '', '/');
    }
  }, []);

  const handleError = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError && err.status === 401) {
        logout();
        return;
      }
      toasts.push(errorMessage(err));
    },
    [logout, toasts],
  );

  const { profiles, mergeProfiles } = useProfiles();
  const conversations = useConversations();
  const active = useActiveConversation({
    onError: handleError,
    onProfiles: mergeProfiles,
    onMessageSeen: conversations.noteLastMessage,
    onReadMarked: conversations.markRead,
    myUserId: auth?.userId ?? '',
    myTenantId: auth?.currentTenantId ?? '',
  });

  // Depend on the stable inner callbacks, not the hook objects (which are
  // recreated every render) — otherwise the connection effect loops forever.
  const { handleListEvent, applySettings, removeConversation } = conversations;
  const { handleMessageEvent, isActive, close: closeActive } = active;

  const handleRealtimeEvent = useCallback(
    (conversationId: string, event: RealtimeEvent) => {
      handleListEvent(conversationId, event, isActive(conversationId));
      handleMessageEvent(event);
      if (event.type === 'CONVERSATION_DELETED' && isActive(conversationId)) {
        closeActive();
      }
    },
    [handleListEvent, handleMessageEvent, isActive, closeActive],
  );

  const handleSyncEvent = useCallback(
    (event: RealtimeEvent) => handleMessageEvent(event),
    [handleMessageEvent],
  );

  const { status, realtime, reconnect } = useRealtimeConnection(auth, handleSyncEvent);
  const [searchOpen, setSearchOpen] = useState(false);
  const [profileCard, setProfileCard] = useState<{ userId: string; role?: string } | null>(null);
  const [headerMenu, setHeaderMenu] = useState<{ x: number; y: number } | null>(null);
  const [mainMenu, setMainMenu] = useState<{ x: number; y: number } | null>(null);
  const [highlightId, setHighlightId] = useState<string | null>(null);

  // Search result → open conversation → page history → scroll + flash.
  const jumpToMessage = useCallback(
    async (conversationId: string, messageId: string) => {
      setSearchOpen(false);
      setHighlightId(null);
      if (!isActive(conversationId)) {
        await active.open(conversationId, realtime);
      }
      const found = await active.jumpToMessage(messageId);
      if (found) {
        setHighlightId(messageId);
        setTimeout(() => setHighlightId(null), 2500);
      } else {
        toasts.push('Message not loaded yet — load older messages to find it');
      }
    },
    [isActive, active, realtime, toasts],
  );

  useEffect(() => {
    if (!auth || !realtime) return;
    conversations.reset();
    active.close();
    setSearchOpen(false);
    conversations
      .load(realtime, handleRealtimeEvent)
      .then(list => loadDmPeerProfiles(list, auth.userId, mergeProfiles))
      .catch(handleError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth, realtime]);

  const onCreated = useCallback(
    (conversation: Conversation) => {
      setDialog(null);
      conversations.addConversation(conversation, realtime, handleRealtimeEvent);
      void active.open(conversation.id, realtime);
    },
    [conversations, active, realtime, handleRealtimeEvent],
  );

  const afterWorkspaceChange = useCallback(
    (tenantId: string) => {
      setDialog(null);
      switchWorkspace(tenantId).catch(handleError);
    },
    [switchWorkspace, handleError],
  );

  // After leaving/deleting the current workspace, fall back to any other
  // membership, or sign out when none remain (no full page reload needed).
  const afterWorkspaceRemoved = useCallback(() => {
    setDialog(null);
    const next = auth?.tenants.find(t => t.id !== auth.currentTenantId);
    if (next) {
      switchWorkspace(next.id).catch(handleError);
    } else {
      logout();
    }
  }, [auth, switchWorkspace, handleError, logout]);

  const [confirmDelete, setConfirmDelete] = useState<Conversation | null>(null);
  const [confirmDeleteBatch, setConfirmDeleteBatch] = useState<Conversation[] | null>(null);
  const [confirmDeleteMessages, setConfirmDeleteMessages] = useState<string[] | null>(null);

  const performDeleteMessages = useCallback(() => {
    const ids = confirmDeleteMessages;
    setConfirmDeleteMessages(null);
    if (!ids || ids.length === 0) return;
    void active.deleteMessages(ids);
  }, [confirmDeleteMessages, active]);

  const updateConversationSettings = useCallback(
    (conversation: Conversation, patch: ConversationSettingsPatch) => {
      const before = {
        pinned: conversation.pinned,
        muted: conversation.muted,
        archived: conversation.archived,
      };
      applySettings(conversation.id, patch);
      api
        .updateConversationSettings(conversation.id, patch)
        .then(() => {
          if (patch.archived !== undefined) {
            toasts.push(patch.archived ? 'Conversation archived' : 'Conversation unarchived', 'success');
          }
        })
        .catch(err => {
          applySettings(conversation.id, before);
          handleError(err);
        });
    },
    [applySettings, handleError, toasts],
  );

  const performDelete = useCallback(() => {
    const conversation = confirmDelete;
    setConfirmDelete(null);
    if (!conversation) return;
    api
      .deleteConversation(conversation.id)
      .then(() => {
        removeConversation(conversation.id);
        if (isActive(conversation.id)) closeActive();
        setDialog(null);
        toasts.push('Conversation deleted', 'success');
      })
      .catch(handleError);
  }, [confirmDelete, removeConversation, isActive, closeActive, handleError, toasts]);

  const performDeleteBatch = useCallback(() => {
    const batch = confirmDeleteBatch;
    setConfirmDeleteBatch(null);
    if (!batch || batch.length === 0) return;
    void Promise.allSettled(batch.map(c => api.deleteConversation(c.id))).then(results => {
      let deleted = 0;
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          deleted += 1;
          removeConversation(batch[index].id);
          if (isActive(batch[index].id)) closeActive();
        }
      });
      const failed = batch.length - deleted;
      if (failed === 0) {
        toasts.push(`Deleted ${deleted} conversation${deleted > 1 ? 's' : ''}`, 'success');
      } else {
        toasts.push(`Deleted ${deleted}, failed ${failed} (owner-only groups?)`);
      }
    });
  }, [confirmDeleteBatch, removeConversation, isActive, closeActive, toasts]);

  if (!auth) return null;

  const myRole = active.detail?.members.find(m => m.userId === auth.userId)?.role ?? 'MEMBER';
  const activeMemberCount = active.detail?.members.filter(m => !m.leftAt).length ?? 0;
  const title = conversationTitle(active.detail, profiles, auth.userId);
  const seenMessageId = computeSeenMessageId(active.detail, active.messages, auth.userId);

  const openProfileCard = (userId: string) => {
    const role = active.detail?.members.find(m => m.userId === userId)?.role;
    setProfileCard({ userId, role });
  };

  const buildHeaderMenu = (): ContextMenuItem[] => {
    const detail = active.detail;
    if (!detail) return [];
    const items: ContextMenuItem[] = [
      { label: 'Search messages', onClick: () => setSearchOpen(open => !open) },
      { label: 'Conversation settings', onClick: () => setDialog('conv-settings') },
      {
        label: 'Copy conversation name',
        onClick: () => copyText(title),
      },
      { label: 'Reload conversations', onClick: () => conversations.reload() },
    ];
    if (detail.type === 'DM' || myRole === 'OWNER') {
      items.push({
        label: 'Delete conversation',
        danger: true,
        onClick: () => setConfirmDelete(detail),
      });
    }
    return items;
  };

  const buildMainMenu = (): ContextMenuItem[] => [
    { label: 'New direct message', onClick: () => setDialog('dm') },
    { label: 'New group', onClick: () => setDialog('group') },
    { label: 'Reload conversations', onClick: () => conversations.reload() },
    { label: 'Workspace settings', onClick: () => setDialog('ws-settings') },
    { label: 'My profile', onClick: () => setDialog('profile') },
  ];

  return (
    <div className="chat-layout">
      <Sidebar
        auth={auth}
        conversations={conversations.conversations}
        profiles={profiles}
        lastMessages={conversations.lastMessages}
        activeId={active.activeId}
        status={status}
        onOpenProfile={() => setDialog('profile')}
        onSelect={id => void active.open(id, realtime)}
        onNewDm={() => setDialog('dm')}
        onNewGroup={() => setDialog('group')}
        onSwitchWorkspace={tenantId => switchWorkspace(tenantId).catch(handleError)}
        onCreateWorkspace={() => setDialog('create-ws')}
        onJoinWorkspace={() => setDialog('join-ws')}
        onWorkspaceSettings={() => setDialog('ws-settings')}
        onUpdateSettings={updateConversationSettings}
        onDeleteConversation={conversation => setConfirmDelete(conversation)}
        onDeleteConversations={batch => setConfirmDeleteBatch(batch)}
        onLogout={logout}
      />

      <main
        className="chat-main"
        onContextMenu={event => {
          // Only on empty background — rows/headers handle their own menus.
          if (event.target === event.currentTarget) {
            event.preventDefault();
            setMainMenu({ x: event.clientX, y: event.clientY });
          }
        }}
      >
        {active.activeId && active.detail ? (
          <>
            <header
              className="chat-header"
              onContextMenu={event => {
                event.preventDefault();
                setHeaderMenu({ x: event.clientX, y: event.clientY });
              }}
            >
              <div key={active.detail.id} className="chat-header-info">
                <Avatar name={title} seed={active.detail.id} size={38} />
                <div>
                  <h2>{title}</h2>
                  <span className="muted">
                    {active.detail.type === 'GROUP'
                      ? `${activeMemberCount} members`
                      : 'Direct message'}
                    {' · '}
                    <span className={status === 'connected' ? 'online-text' : ''}>
                      {status === 'connected'
                        ? 'online'
                        : status === 'connecting'
                          ? 'reconnecting…'
                          : 'offline'}
                    </span>
                  </span>
                </div>
              </div>
              <div className="header-actions">
                {status === 'offline' && (
                  <button className="reconnect-btn" onClick={reconnect}>
                    <RefreshIcon /> Reconnect
                  </button>
                )}
                <button
                  className="icon-btn"
                  title="Search messages"
                  onClick={() => setSearchOpen(open => !open)}
                >
                  <SearchIcon />
                </button>
                <button
                  className="icon-btn settings-btn"
                  title="Conversation settings"
                  onClick={() => setDialog('conv-settings')}
                >
                  <GearIcon />
                </button>
              </div>
            </header>
            {searchOpen && (
              <SearchPanel
                conversationId={active.detail.id}
                profiles={profiles}
                onClose={() => setSearchOpen(false)}
                onError={handleError}
                onJump={jumpToMessage}
              />
            )}
            <MessageList
              key={active.detail.id}
              messages={active.messages}
              profiles={profiles}
              myUserId={auth.userId}
              canModerate={myRole === 'OWNER'}
              hasMore={active.hasMore}
              loading={active.loading}
              seenMessageId={seenMessageId}
              highlightId={highlightId}
              onLoadOlder={() => active.loadOlder()}
              onReply={active.selectReply}
              onEdit={active.selectEdit}
              onDelete={message => setConfirmDeleteMessages([message.id])}
              onDeleteMessages={ids => setConfirmDeleteMessages(ids)}
              onRetry={message => void active.retrySend(message)}
              onSenderClick={openProfileCard}
            />
            <MessageInput
              editing={active.editing}
              replyingTo={active.replyingTo}
              replyAuthor={
                active.replyingTo
                  ? profiles[active.replyingTo.senderId]?.displayName ??
                    active.replyingTo.senderId.slice(0, 8)
                  : ''
              }
              onSend={content => void active.send(content)}
              onCancelEdit={active.cancelEdit}
              onCancelReply={active.cancelReply}
            />
          </>
        ) : (
          <div className="empty-state">
            <div className="empty-art">
              <ChatIcon size={64} />
            </div>
            <h2>Welcome to ChitChat</h2>
            <p>Pick a conversation from the sidebar, or start a new one.</p>
          </div>
        )}
      </main>

      {dialog === 'dm' && (
        <NewConversation mode="dm" onClose={() => setDialog(null)} onCreated={onCreated} />
      )}
      {dialog === 'group' && (
        <NewConversation mode="group" onClose={() => setDialog(null)} onCreated={onCreated} />
      )}
      {(dialog === 'create-ws' || dialog === 'join-ws') && (
        <WorkspaceDialog
          mode={dialog === 'create-ws' ? 'create' : 'join'}
          initialToken={dialog === 'join-ws' ? joinToken : undefined}
          onClose={() => {
            setDialog(null);
            setJoinToken('');
          }}
          onJoined={afterWorkspaceChange}
        />
      )}
      {dialog === 'ws-settings' && (
        <WorkspaceSettings
          auth={auth}
          profiles={profiles}
          onProfilesLoaded={mergeProfiles}
          onClose={() => setDialog(null)}
          onChanged={() => switchWorkspace(auth.currentTenantId).catch(handleError)}
          onDeleted={afterWorkspaceRemoved}
        />
      )}
      {dialog === 'profile' && (
        <ProfileDialog
          auth={auth}
          onClose={() => setDialog(null)}
          onUpdated={() => {
            setDialog(null);
            switchWorkspace(auth.currentTenantId).catch(handleError);
          }}
          onDeleted={logout}
          onError={handleError}
        />
      )}
      {profileCard && (
        <UserProfileCard
          profile={profiles[profileCard.userId] ?? null}
          userId={profileCard.userId}
          role={profileCard.role}
          onClose={() => setProfileCard(null)}
        />
      )}
      {dialog === 'conv-settings' && active.detail && (
        <ConversationSettings
          conversation={active.detail}
          myUserId={auth.userId}
          profiles={profiles}
          onProfilesLoaded={mergeProfiles}
          onClose={() => setDialog(null)}
          onUpdated={active.setDetail}
          onLeft={() => {
            setDialog(null);
            active.close();
            conversations.reload();
            toasts.push('Left conversation', 'success');
          }}
          onDelete={
            active.detail.type === 'DM' || myRole === 'OWNER'
              ? () => setConfirmDelete(active.detail)
              : undefined
          }
          onViewProfile={userId => {
            setDialog(null);
            openProfileCard(userId);
          }}
          pushSuccess={message => toasts.push(message, 'success')}
        />
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="Delete conversation?"
          message={
            confirmDelete.type === 'GROUP'
              ? `"${confirmDelete.name ?? 'Group'}" and all of its messages will be permanently removed for everyone.`
              : 'This direct message will be permanently removed for both participants.'
          }
          confirmLabel="Delete"
          onConfirm={performDelete}
          onClose={() => setConfirmDelete(null)}
        />
      )}
      {confirmDeleteBatch && (
        <ConfirmDialog
          title={`Delete ${confirmDeleteBatch.length} conversations?`}
          message="Messages and membership for every selected conversation will be permanently removed. Groups you don't own will be skipped."
          confirmLabel="Delete all"
          onConfirm={performDeleteBatch}
          onClose={() => setConfirmDeleteBatch(null)}
        />
      )}
      {confirmDeleteMessages && (
        <ConfirmDialog
          title={`Delete ${confirmDeleteMessages.length} message${confirmDeleteMessages.length > 1 ? 's' : ''}?`}
          message="Deleted messages are removed for everyone in this conversation and cannot be recovered."
          confirmLabel={confirmDeleteMessages.length > 1 ? 'Delete all' : 'Delete'}
          onConfirm={performDeleteMessages}
          onClose={() => setConfirmDeleteMessages(null)}
        />
      )}

      {headerMenu && (
        <ContextMenu
          x={headerMenu.x}
          y={headerMenu.y}
          items={buildHeaderMenu()}
          onClose={() => setHeaderMenu(null)}
        />
      )}
      {mainMenu && (
        <ContextMenu
          x={mainMenu.x}
          y={mainMenu.y}
          items={buildMainMenu()}
          onClose={() => setMainMenu(null)}
        />
      )}

      <ToastStack toasts={toasts.toasts} />
    </div>
  );
}

function loadDmPeerProfiles(
  list: Conversation[],
  myUserId: string,
  mergeProfiles: (users: UserProfile[]) => void,
) {
  const peerIds = list
    .map(c => (c.type === 'DM' ? dmPeerId(c, myUserId) : null))
    .filter((id): id is string => Boolean(id));
  if (peerIds.length > 0) {
    void api.getProfiles(peerIds).then(mergeProfiles).catch(() => undefined);
  }
}

/** The id of my latest message that every other active member has read. */
function computeSeenMessageId(
  detail: Conversation | null,
  messages: { id: string; senderId: string; sequence: number }[],
  myUserId: string,
): string | null {
  if (!detail) return null;
  const others = detail.members.filter(m => !m.leftAt && m.userId !== myUserId);
  if (others.length === 0) return null;
  const minRead = Math.min(...others.map(m => m.lastReadSequence));
  for (let i = messages.length - 1; i >= 0; i--) {
    const message = messages[i];
    if (message.senderId === myUserId) {
      return message.sequence <= minRead ? message.id : null;
    }
  }
  return null;
}
