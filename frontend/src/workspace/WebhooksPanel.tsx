import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import ContextMenu, { type ContextMenuItem } from '../components/ContextMenu';
import { EditIcon, TrashIcon } from '../components/icons';
import { useContextMenu } from '../hooks/useContextMenu';
import { copyText } from '../lib/ui';

const AVAILABLE_EVENTS = [
  'message.sent',
  'message.updated',
  'message.deleted',
  'conversation.created',
];

interface WebhookRow {
  id: string;
  url: string;
  events: string[];
  active: boolean;
}

/** Owner/Admin webhook management inside workspace settings. */
export default function WebhooksPanel({ onError }: { onError: (err: unknown) => void }) {
  const [webhooks, setWebhooks] = useState<WebhookRow[]>([]);
  const [url, setUrl] = useState('');
  const [events, setEvents] = useState<Set<string>>(new Set(['message.sent']));
  const [secret, setSecret] = useState('');
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const { menu, openMenu, closeMenu } = useContextMenu<WebhookRow>();

  const load = useCallback(() => {
    api
      .listWebhooks()
      .then(rows => setWebhooks(rows as WebhookRow[]))
      .catch(() => setWebhooks([]));
  }, []);

  useEffect(() => load(), [load]);

  function toggleEvent(event: string) {
    setEvents(prev => {
      const next = new Set(prev);
      if (next.has(event)) next.delete(event);
      else next.add(event);
      return next;
    });
  }

  function startEdit(hook: WebhookRow) {
    setEditingId(hook.id);
    setUrl(hook.url);
    setEvents(new Set(hook.events));
    setSecret('');
  }

  function cancelEdit() {
    setEditingId(null);
    setUrl('');
    setEvents(new Set(['message.sent']));
    setSecret('');
  }

  async function save() {
    setBusy(true);
    try {
      if (editingId) {
        await api.updateWebhook(editingId, {
          url: url.trim(),
          events: [...events],
        });
      } else {
        await api.registerWebhook(url.trim(), [...events], secret.trim() || undefined);
      }
      cancelEdit();
      load();
    } catch (err) {
      onError(err);
    } finally {
      setBusy(false);
    }
  }

  const hookMenuItems = (hook: WebhookRow): ContextMenuItem[] => [
    { label: 'Edit endpoint', icon: <EditIcon />, onClick: () => startEdit(hook) },
    {
      label: hook.active ? 'Disable' : 'Enable',
      onClick: () =>
        void api.updateWebhook(hook.id, { active: !hook.active }).then(load).catch(onError),
    },
    {
      label: 'Copy URL',
      onClick: () => copyText(hook.url),
    },
    {
      label: 'Delete endpoint',
      icon: <TrashIcon />,
      danger: true,
      onClick: () => void api.deleteWebhook(hook.id).then(load).catch(onError),
    },
  ];

  return (
    <section className="settings-section">
      <h4>Webhooks</h4>
      {webhooks.length === 0 && <p className="muted">No webhook endpoints registered.</p>}
      <div className="member-list">
        {webhooks.map(hook => (
          <div
            key={hook.id}
            className={editingId === hook.id ? 'member-row editing' : 'member-row'}
            onContextMenu={event => openMenu(event, hook)}
          >
            <div className="member-info">
              <strong className="webhook-url">{hook.url}</strong>
              <span className="muted">{hook.events.join(', ')}</span>
            </div>
            <span className={hook.active ? 'role-badge admin' : 'role-badge member'}>
              {hook.active ? 'ACTIVE' : 'OFF'}
            </span>
            <button
              className="icon-btn"
              title={hook.active ? 'Disable' : 'Enable'}
              onClick={() =>
                void api.updateWebhook(hook.id, { active: !hook.active }).then(load).catch(onError)
              }
            >
              ⏻
            </button>
            <button className="icon-btn" title="Edit webhook" onClick={() => startEdit(hook)}>
              <EditIcon />
            </button>
            <button
              className="icon-btn danger-hover"
              title="Delete webhook"
              onClick={() => void api.deleteWebhook(hook.id).then(load).catch(onError)}
            >
              <TrashIcon />
            </button>
          </div>
        ))}
      </div>

      <div className="webhook-form">
        <input
          value={url}
          onChange={e => setUrl(e.target.value)}
          placeholder="https://example.com/hooks/chitchat"
        />
        <div className="event-checks">
          {AVAILABLE_EVENTS.map(event => (
            <label key={event} className="event-check">
              <input
                type="checkbox"
                checked={events.has(event)}
                onChange={() => toggleEvent(event)}
              />
              {event}
            </label>
          ))}
        </div>
        <div className="inline-row">
          {!editingId && (
            <input
              value={secret}
              onChange={e => setSecret(e.target.value)}
              placeholder="HMAC secret (optional)"
              type="password"
            />
          )}
          <button
            className="primary"
            disabled={busy || !url.trim() || events.size === 0}
            onClick={() => void save()}
          >
            {editingId ? 'Save changes' : 'Register'}
          </button>
          {editingId && (
            <button disabled={busy} onClick={cancelEdit}>
              Cancel
            </button>
          )}
        </div>
      </div>

      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          items={hookMenuItems(menu.target)}
          onClose={closeMenu}
        />
      )}
    </section>
  );
}
