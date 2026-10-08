import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { TrashIcon } from '../components/icons';
import { useContextMenu } from '../hooks/useContextMenu';
import { formatShortDate } from '../lib/format';
import { buildInviteLink } from '../lib/invite';
import { copyText } from '../lib/ui';

interface InviteRow {
  token: string;
  createdBy: string;
  expiresAt: string;
  createdAt: string;
}

/** Active invite links with revocation, for owners/admins. */
export default function InvitesPanel({ tenantId, onError }: { tenantId: string; onError: (err: unknown) => void }) {
  const [invites, setInvites] = useState<InviteRow[]>([]);
  const [copied, setCopied] = useState<string | null>(null);
  const { menu, openMenu, closeMenu } = useContextMenu<InviteRow>();

  const load = useCallback(() => {
    api.listInvites(tenantId).then(setInvites).catch(() => setInvites([]));
  }, [tenantId]);

  useEffect(() => load(), [load]);

  const copyToken = (token: string) => {
    copyText(token);
    setCopied(token);
    setTimeout(() => setCopied(null), 1500);
  };

  const inviteMenuItems = (invite: InviteRow): ContextMenuItem[] => [
    { label: 'Copy token', onClick: () => copyToken(invite.token) },
    {
      label: 'Copy invite link',
      onClick: () => copyText(buildInviteLink(invite.token)),
    },
    {
      label: 'Revoke invite',
      icon: <TrashIcon />,
      danger: true,
      onClick: () =>
        void api.revokeInvite(tenantId, invite.token).then(load).catch(onError),
    },
  ];

  return (
    <section className="settings-section">
      <h4>Active invites</h4>
      {invites.length === 0 && <p className="muted">No active invite tokens.</p>}
      <div className="member-list">
        {invites.map(invite => (
          <div
            key={invite.token}
            className="member-row"
            onContextMenu={event => openMenu(event, invite)}
          >
            <div className="member-info">
              <code className="invite-code">{invite.token.slice(0, 18)}…</code>
              <span className="muted">expires {formatShortDate(invite.expiresAt)}</span>
            </div>
            <button
              className="icon-btn"
              title="Copy invite link"
              onClick={() => copyText(buildInviteLink(invite.token))}
            >
              {copied === invite.token ? '✓' : '⧉'}
            </button>
            <button
              className="icon-btn danger-hover"
              title="Revoke invite"
              onClick={() =>
                void api.revokeInvite(tenantId, invite.token).then(load).catch(onError)
              }
            >
              <TrashIcon />
            </button>
          </div>
        ))}
      </div>

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={inviteMenuItems(menu.target)}
          onClose={closeMenu}
        />
      )}
    </section>
  );
}
