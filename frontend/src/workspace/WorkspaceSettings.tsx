import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { AuthResponse, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import Dialog from '../components/Dialog';
import { buildInviteLink } from '../lib/invite';
import { copyText, errorMessage } from '../lib/ui';
import WebhooksPanel from './WebhooksPanel';
import InvitesPanel from './InvitesPanel';

interface WorkspaceSettingsProps {
  auth: AuthResponse;
  profiles: Record<string, UserProfile>;
  onProfilesLoaded: (users: UserProfile[]) => void;
  onClose: () => void;
  onChanged: () => void;
  onDeleted: () => void;
}

interface MemberRow {
  userId: string;
  role: string;
  joinedAt: string;
}

export default function WorkspaceSettings({
  auth,
  profiles,
  onProfilesLoaded,
  onClose,
  onChanged,
  onDeleted,
}: WorkspaceSettingsProps) {
  const tenantId = auth.currentTenantId;
  const myRole = auth.tenants.find(t => t.id === tenantId)?.role ?? 'MEMBER';
  const isOwner = myRole === 'OWNER';
  const canInvite = isOwner || myRole === 'ADMIN';

  const [members, setMembers] = useState<MemberRow[]>([]);
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [wsName, setWsName] = useState(auth.currentTenantName);
  const [wsDescription, setWsDescription] = useState('');
  const currentSlug = auth.tenants.find(t => t.id === tenantId)?.slug ?? '';

  const load = useCallback(async () => {
    try {
      const rows = await api.getWorkspaceMembers(tenantId);
      setMembers(rows);
      const missing = rows.map(r => r.userId).filter(id => !profiles[id]);
      if (missing.length > 0) {
        onProfilesLoaded(await api.getProfiles(missing));
      }
    } catch (err) {
      setError(errorMessage(err, 'Failed to load members'));
    }
  }, [tenantId, profiles, onProfilesLoaded]);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId]);

  async function generateInvite() {
    try {
      const invite = await api.generateInvite(tenantId);
      setInviteToken(invite.token);
      setCopied(false);
    } catch (err) {
      setError(errorMessage(err, 'Failed to generate invite'));
    }
  }

  async function removeMember(userId: string) {
    try {
      await api.removeWorkspaceMember(tenantId, userId);
      await load();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, 'Failed to remove member'));
    }
  }

  async function leave() {
    try {
      await api.leaveWorkspace(tenantId);
      onDeleted();
    } catch (err) {
      setError(errorMessage(err, 'Failed to leave'));
    }
  }

  async function destroy() {
    try {
      await api.deleteWorkspace(tenantId);
      onDeleted();
    } catch (err) {
      setError(errorMessage(err, 'Failed to delete'));
    }
  }

  return (
    <Dialog onClose={onClose} className="wide">
      <h3>{auth.currentTenantName} — settings</h3>

      {isOwner && (
        <section className="settings-section">
          <h4>Workspace</h4>
          <label>
            Name
            <input value={wsName} onChange={event => setWsName(event.target.value)} />
          </label>
          <label>
            Description
            <input
              value={wsDescription}
              onChange={event => setWsDescription(event.target.value)}
              placeholder="What is this workspace for?"
            />
          </label>
          <button
            disabled={!wsName.trim() || wsName.trim() === auth.currentTenantName}
            onClick={() => {
              void api
                .updateWorkspace(tenantId, wsName.trim(), currentSlug, wsDescription.trim())
                .then(() => {
                  onChanged();
                  setError(null);
                })
                .catch(err =>
                  setError(errorMessage(err, 'Failed to update')),
                );
            }}
          >
            Save changes
          </button>
        </section>
      )}

      <section className="settings-section">
        <h4>Members</h4>
        <div className="member-list">
          {members.map(member => {
            const profile = profiles[member.userId];
            return (
              <div key={member.userId} className="member-row">
                <Avatar
                  name={profile?.displayName ?? member.userId}
                  seed={member.userId}
                  size={32}
                />
                <div className="member-info">
                  <strong>{profile?.displayName ?? member.userId.slice(0, 8)}</strong>
                  <span className="muted">@{profile?.username ?? '…'}</span>
                </div>
                <span className={`role-badge ${member.role.toLowerCase()}`}>{member.role}</span>
                {isOwner && member.userId !== auth.userId && (
                  <button
                    className="icon-btn danger-hover"
                    title="Remove member"
                    onClick={() => void removeMember(member.userId)}
                  >
                    ×
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </section>

      <section className="settings-section">
        <h4>Roles</h4>
        <div className="roles-legend">
          <p><span className="role-badge owner">OWNER</span> Full control: members, settings, ownership transfer, delete workspace</p>
          <p><span className="role-badge admin">ADMIN</span> Invite people, manage invites and webhooks</p>
          <p><span className="role-badge member">MEMBER</span> Chat, create and manage conversations</p>
        </div>
      </section>

      {canInvite && (
        <section className="settings-section">
          <h4>Invite people</h4>
          {inviteToken ? (
            <div className="invite-token-box">
              <code>{buildInviteLink(inviteToken)}</code>
              <button
                onClick={() => {
                  copyText(buildInviteLink(inviteToken));
                  setCopied(true);
                }}
              >
                {copied ? 'Copied ✓' : 'Copy link'}
              </button>
              <button
                onClick={() => {
                  copyText(inviteToken);
                }}
              >
                Copy token
              </button>
            </div>
          ) : (
            <button onClick={() => void generateInvite()}>Generate invite link (7 days)</button>
          )}
          <p className="muted">
            Share the link — opening it joins the holder as a MEMBER, once. Works from the join
            dialog too (paste the whole link).
          </p>
        </section>
      )}

      {canInvite && <InvitesPanel tenantId={tenantId} onError={err => setError(errorMessage(err, 'Failed'))} />}

      {canInvite && <WebhooksPanel onError={err => setError(errorMessage(err, 'Failed'))} />}

      {error && <p className="error">{error}</p>}

      <section className="settings-section danger-zone">
        <h4>Danger zone</h4>
        <div className="dialog-actions left">
          {!isOwner && <button onClick={() => void leave()}>Leave workspace</button>}
          {isOwner && !confirmDelete && (
            <button className="danger" onClick={() => setConfirmDelete(true)}>
              Delete workspace
            </button>
          )}
          {isOwner && confirmDelete && (
            <>
              <span className="muted">Really delete? This blocks everyone.</span>
              <button className="danger" onClick={() => void destroy()}>
                Yes, delete
              </button>
              <button onClick={() => setConfirmDelete(false)}>Cancel</button>
            </>
          )}
        </div>
      </section>

      <div className="dialog-actions">
        <button onClick={onClose}>Close</button>
      </div>
    </Dialog>
  );
}
