import type { UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { useContextMenu } from '../hooks/useContextMenu';
import { formatMonthYear } from '../lib/format';
import { copyText } from '../lib/ui';

interface UserProfileCardProps {
  profile: UserProfile | null;
  userId: string;
  role?: string;
  onClose: () => void;
}

/** Read-only profile popover for any workspace member. */
export default function UserProfileCard({ profile, userId, role, onClose }: UserProfileCardProps) {
  const { menu, openMenu, closeMenu } = useContextMenu<void>();

  const menuItems: ContextMenuItem[] = [
    {
      label: 'Copy username',
      onClick: () => copyText(profile?.username ?? userId),
    },
    {
      label: 'Copy user ID',
      onClick: () => copyText(userId),
    },
  ];

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        className="dialog profile-card"
        onClick={event => event.stopPropagation()}
        onContextMenu={event => openMenu(event, undefined)}
      >
        <div className="profile-head">
          <Avatar name={profile?.displayName ?? userId} seed={userId} size={64} />
          <div>
            <h3>{profile?.displayName ?? userId.slice(0, 8)}</h3>
            <span className="muted">@{profile?.username ?? 'unknown'}</span>
          </div>
        </div>
        {role && (
          <p>
            <span className={`role-badge ${role.toLowerCase()}`}>{role}</span>
          </p>
        )}
        {profile?.createdAt && (
          <p className="muted">Member since {formatMonthYear(profile.createdAt)}</p>
        )}
        <div className="dialog-actions">
          <button onClick={onClose}>Close</button>
        </div>
      </div>

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={menuItems}
          onClose={closeMenu}
        />
      )}
    </div>
  );
}
