// @vitest-environment jsdom
import { beforeAll, describe, expect, it } from 'vitest';
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthContext';

beforeAll(() => {
  (globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;
});

describe('App smoke test', () => {
  it('mounts without crashing and renders the login page', () => {
    const container = document.createElement('div');
    document.body.appendChild(container);

    const root = createRoot(container);
    act(() => {
      root.render(
        React.createElement(
          React.StrictMode,
          null,
          React.createElement(AuthProvider, null, React.createElement(App)),
        ),
      );
    });

    expect(container.textContent).toContain('ChitChat');
    act(() => root.unmount());
  });
});
