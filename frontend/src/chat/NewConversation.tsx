import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Conversation, UserProfile } from '../api/types';
import Avatar from '../components/Avatar';
import Dialog from '../components/Dialog';
import { errorMessage } from '../lib/ui';

interface NewConversationProps {
  mode: 'dm' | 'group';
  onClose: () => void;
  onCreated: (conversation: Conversation) => void;
}

export default function NewConversation({ mode, onClose, onCreated }: NewConversationProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserProfile[]>([]);
  const [selected, setSelected] = useState<UserProfile[]>([]);
  const [groupName, setGroupName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const handle = setTimeout(() => {
      api
        .searchUsers(query.trim())
        .then(users =>
          setResults(users.filter(user => !selected.some(s => s.id === user.id))),
        )
        .catch(() => setResults([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [query, selected]);

  async function pickUser(user: UserProfile) {
    if (mode === 'dm') {
      setBusy(true);
      setError(null);
      try {
        onCreated(await api.createDm(user.id));
      } catch (err) {
        setError(errorMessage(err, 'Failed to create conversation'));
        setBusy(false);
      }
    } else {
      setSelected(prev => [...prev, user]);
      setQuery('');
      setResults([]);
    }
  }

  async function createGroup() {
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createGroup(groupName.trim(), selected.map(user => user.id)));
    } catch (err) {
      setError(errorMessage(err, 'Failed to create group'));
      setBusy(false);
    }
  }

  return (
    <Dialog onClose={onClose}>
      <h3>{mode === 'dm' ? 'New direct message' : 'New group'}</h3>

      {mode === 'group' && (
        <label>
          Group name
          <input value={groupName} onChange={event => setGroupName(event.target.value)} />
        </label>
      )}

      {mode === 'group' && selected.length > 0 && (
        <div className="chips">
          {selected.map(user => (
            <span key={user.id} className="chip">
              {user.displayName}
              <button onClick={() => setSelected(prev => prev.filter(s => s.id !== user.id))}>
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      <label>
        Find people
        <input
          value={query}
          onChange={event => setQuery(event.target.value)}
          placeholder="Search by name or username"
          autoFocus
        />
      </label>

      <div className="search-results">
        {results.map(user => (
          <button key={user.id} className="search-result" onClick={() => void pickUser(user)} disabled={busy}>
            <Avatar name={user.displayName} seed={user.id} size={30} />
            <strong>{user.displayName}</strong>
            <span className="muted">@{user.username}</span>
          </button>
        ))}
        {query.trim().length >= 2 && results.length === 0 && <p className="muted">No matches.</p>}
      </div>

      {error && <p className="error">{error}</p>}

      <div className="dialog-actions">
        <button onClick={onClose}>Cancel</button>
        {mode === 'group' && (
          <button
            className="primary"
            disabled={busy || !groupName.trim() || selected.length === 0}
            onClick={() => void createGroup()}
          >
            Create group
          </button>
        )}
      </div>
    </Dialog>
  );
}
