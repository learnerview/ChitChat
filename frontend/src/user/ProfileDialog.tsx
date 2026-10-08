import { useState } from 'react';
import { api } from '../api/client';
import type { AuthResponse } from '../api/types';
import Avatar from '../components/Avatar';
import Dialog from '../components/Dialog';
import { errorMessage } from '../lib/ui';

interface ProfileDialogProps {
  auth: AuthResponse;
  onClose: () => void;
  onUpdated: (displayName: string) => void;
  onDeleted: () => void;
  onError: (err: unknown) => void;
}

/** Own account: display name, password change, account deletion. */
export default function ProfileDialog({ auth, onClose, onUpdated, onDeleted, onError }: ProfileDialogProps) {
  const [displayName, setDisplayName] = useState(auth.displayName);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      setNote(success);
      setError(null);
    } catch (err) {
      setError(errorMessage(err, 'Action failed'));
      setNote(null);
    }
  }

  return (
    <Dialog onClose={onClose}>
      <div className="profile-head">
        <Avatar name={auth.displayName} seed={auth.userId} size={56} />
        <div>
          <h3>{auth.displayName}</h3>
          <span className="muted">@{auth.username}</span>
        </div>
      </div>

      <section className="settings-section">
        <h4>Display name</h4>
        <div className="inline-row">
          <input value={displayName} onChange={event => setDisplayName(event.target.value)} />
          <button
            className="primary"
            disabled={!displayName.trim() || displayName.trim() === auth.displayName}
            onClick={() =>
              void run(async () => {
                await api.updateProfile(displayName.trim());
                onUpdated(displayName.trim());
              }, 'Profile updated')
            }
          >
            Save
          </button>
        </div>
      </section>

      <section className="settings-section">
        <h4>Change password</h4>
        <label>
          Current password
          <input
            type="password"
            value={currentPassword}
            onChange={event => setCurrentPassword(event.target.value)}
          />
        </label>
        <label>
          New password (8+ characters)
          <input
            type="password"
            value={newPassword}
            onChange={event => setNewPassword(event.target.value)}
          />
        </label>
        <button
          disabled={!currentPassword || newPassword.length < 8}
          onClick={() =>
            void run(async () => {
              await api.changePassword(currentPassword, newPassword);
              setCurrentPassword('');
              setNewPassword('');
            }, 'Password changed — other sessions were signed out')
          }
        >
          Change password
        </button>
      </section>

      {note && <p className="success">{note}</p>}
      {error && <p className="error">{error}</p>}

      <section className="settings-section danger-zone">
        <h4>Danger zone</h4>
        {!confirmDelete ? (
          <button className="danger" onClick={() => setConfirmDelete(true)}>
            Delete my account
          </button>
        ) : (
          <div className="dialog-actions left">
            <span className="muted">This cannot be undone.</span>
            <button
              className="danger"
              onClick={() => {
                void api.deleteAccount().then(onDeleted).catch(onError);
              }}
            >
              Yes, delete everything
            </button>
            <button onClick={() => setConfirmDelete(false)}>Cancel</button>
          </div>
        )}
      </section>

      <div className="dialog-actions">
        <button onClick={onClose}>Close</button>
      </div>
    </Dialog>
  );
}
