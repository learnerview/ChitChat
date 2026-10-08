import { useState, type FormEvent } from 'react';
import { useAuth } from './AuthContext';
import { ChatIcon } from '../components/icons';
import { errorMessage } from '../lib/ui';

export default function LoginPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(username.trim(), password);
      } else {
        await register(username.trim(), displayName.trim(), password);
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-hero">
        <div className="hero-glow" />
        <div className="hero-content">
          <div className="hero-logo">
            <ChatIcon size={56} />
          </div>
          <h1>ChitChat</h1>
          <p className="hero-tagline">
            Realtime team messaging with workspaces, direct messages, groups,
            and delivery you can rely on.
          </p>
          <ul className="hero-points">
            <li>Workspaces with roles and invites</li>
            <li>Instant delivery with automatic recovery</li>
            <li>Never lose a message on reconnect</li>
          </ul>
        </div>
      </div>

      <div className="login-form-side">
        <form className="login-card" onSubmit={submit}>
          <h2>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
          <div className="tabs">
            <button
              type="button"
              className={mode === 'login' ? 'tab active' : 'tab'}
              onClick={() => setMode('login')}
            >
              Sign in
            </button>
            <button
              type="button"
              className={mode === 'register' ? 'tab active' : 'tab'}
              onClick={() => setMode('register')}
            >
              Register
            </button>
          </div>

          <label>
            Username
            <input
              value={username}
              onChange={event => setUsername(event.target.value)}
              autoComplete="username"
              required
              minLength={3}
            />
          </label>

          {mode === 'register' && (
            <label>
              Display name
              <input
                value={displayName}
                onChange={event => setDisplayName(event.target.value)}
                required
              />
            </label>
          )}

          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={event => setPassword(event.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={8}
            />
          </label>

          {error && <p className="error">{error}</p>}

          <button className="primary big" type="submit" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  );
}
