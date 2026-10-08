import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, loadStoredAuth, storeAuth, clearStoredAuth } from './client';
import type { AuthResponse } from './types';

const auth: AuthResponse = {
  token: 'jwt-token',
  userId: 'u1',
  username: 'user',
  displayName: 'User',
  currentTenantId: 'tenant-1',
  currentTenantName: 'Tenant',
  tenants: [],
};

function stubFetch(status: number, body: unknown) {
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return vi.fn().mockResolvedValue(
    new Response(text, {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

describe('api client', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key),
      clear: () => store.clear(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends the JWT and tenant headers on authenticated requests', async () => {
    storeAuth(auth);
    const fetchMock = stubFetch(200, { messages: [], hasMore: false, nextCursor: null });
    vi.stubGlobal('fetch', fetchMock);

    await api.getMessages('c1', null, 30);

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers['Authorization']).toBe('Bearer jwt-token');
    expect(headers['X-Tenant-Id']).toBe('tenant-1');
  });

  it('maps error responses to ApiError with the backend code', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch(403, { code: 'CONVERSATION_ACCESS_DENIED', message: 'denied' }),
    );

    const error = await api.getMessages('c1').catch((err: unknown) => err);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
    expect((error as ApiError).code).toBe('CONVERSATION_ACCESS_DENIED');
    expect((error as ApiError).message).toBe('denied');
  });

  it('survives non-JSON error bodies', async () => {
    vi.stubGlobal('fetch', stubFetch(502, 'Bad Gateway'));

    const error = await api.getMessages('c1').catch((err: unknown) => err);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
  });

  it('returns undefined for 204 responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(api.deleteMessage('m1')).resolves.toBeUndefined();
  });

  it('clears and reloads stored auth', () => {
    storeAuth(auth);
    expect(loadStoredAuth()?.userId).toBe('u1');
    clearStoredAuth();
    expect(loadStoredAuth()).toBeNull();
  });
});
