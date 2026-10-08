import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import Dialog from '../components/Dialog';
import { extractInviteToken } from '../lib/invite';
import { errorMessage } from '../lib/ui';

interface WorkspaceDialogsProps {
  mode: 'create' | 'join';
  onClose: () => void;
  onJoined: (tenantId: string) => void;
  /** Pre-filled token, e.g. from an /invite/:token deep link. */
  initialToken?: string;
}

export default function WorkspaceDialog({ mode, onClose, onJoined, initialToken }: WorkspaceDialogsProps) {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [token, setToken] = useState(initialToken ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function slugify(value: string) {
    return value
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 50);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'create') {
        const workspace = await api.createWorkspace(name.trim(), slug.trim(), description.trim());
        onJoined(workspace.id);
      } else {
        const result = await api.acceptInvite(extractInviteToken(token));
        onJoined(result.tenantId);
      }
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <Dialog onClose={onClose}>
      <h3>{mode === 'create' ? 'Create a workspace' : 'Join a workspace'}</h3>
      <p className="muted">
        {mode === 'create'
          ? 'Workspaces are separate spaces for teams. You become the owner.'
          : 'Paste an invite link or token shared by a workspace owner or admin.'}
      </p>

      <form onSubmit={submit}>
        {mode === 'create' ? (
          <>
            <label>
              Workspace name
              <input
                value={name}
                onChange={event => {
                  setName(event.target.value);
                  setSlug(slugify(event.target.value));
                }}
                required
                autoFocus
              />
            </label>
            <label>
              Slug
              <input
                value={slug}
                onChange={event => setSlug(event.target.value)}
                pattern="[a-z0-9-]+"
                minLength={3}
                required
              />
            </label>
            <label>
              Description (optional)
              <input value={description} onChange={event => setDescription(event.target.value)} />
            </label>
          </>
        ) : (
          <label>
            Invite link or token
            <input
              value={token}
              onChange={event => setToken(event.target.value)}
              placeholder="https://…/invite/…  or  a raw token"
              required
              autoFocus
            />
          </label>
        )}

        {error && <p className="error">{error}</p>}

        <div className="dialog-actions">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="primary" type="submit" disabled={busy}>
            {mode === 'create' ? 'Create workspace' : 'Join workspace'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
