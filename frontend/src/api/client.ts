import type {
  AuthResponse,
  ChatMessage,
  Conversation,
  ConversationMember,
  MessagePage,
  UserProfile,
} from './types';

const AUTH_KEY = 'chitchat.auth';

export function loadStoredAuth(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    return raw ? (JSON.parse(raw) as AuthResponse) : null;
  } catch {
    return null;
  }
}

export function storeAuth(auth: AuthResponse): void {
  localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export function clearStoredAuth(): void {
  localStorage.removeItem(AUTH_KEY);
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const auth = loadStoredAuth();
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    headers['Authorization'] = `Bearer ${auth.token}`;
    headers['X-Tenant-Id'] = auth.currentTenantId;
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    let code = 'ERROR';
    let message = response.statusText;
    try {
      const error = (await response.json()) as { code?: string; message?: string };
      code = error.code ?? code;
      message = error.message ?? message;
    } catch {
      // non-JSON error body
    }
    throw new ApiError(response.status, code, message);
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

export const api = {
  register: (username: string, displayName: string, password: string) =>
    request<string>('POST', '/api/auth/register', { username, displayName, password }),

  login: async (username: string, password: string) => {
    const auth = await request<AuthResponse>('POST', '/api/auth/login', { username, password });
    storeAuth(auth);
    return auth;
  },

  getWsTicket: () => request<{ ticket: string; expiresIn: number }>('POST', '/api/auth/ws-ticket'),

  createWorkspace: (name: string, slug: string, description: string) =>
    request<{ id: string; name: string; slug: string }>('POST', '/api/workspaces', {
      name,
      slug,
      description,
    }),

  getWorkspaceMembers: (tenantId: string) =>
    request<{ userId: string; role: string; joinedAt: string }[]>(
      'GET',
      `/api/workspaces/${tenantId}/members`,
    ),

  removeWorkspaceMember: (tenantId: string, userId: string) =>
    request<void>('DELETE', `/api/workspaces/${tenantId}/members/${userId}`),

  leaveWorkspace: (tenantId: string) =>
    request<void>('POST', `/api/workspaces/${tenantId}/leave`),

  deleteWorkspace: (tenantId: string) => request<void>('DELETE', `/api/workspaces/${tenantId}`),

  acceptInvite: (token: string) =>
    request<{ status: string; tenantId: string }>('POST', '/api/invites/accept', { token }),

  generateInvite: (tenantId: string) =>
    request<{ token: string; expiresAt: string }>('POST', '/api/invites/generate', { tenantId }),

  listInvites: (tenantId: string) =>
    request<{ token: string; createdBy: string; expiresAt: string; createdAt: string }[]>(
      'GET',
      `/api/invites/${tenantId}`,
    ),

  revokeInvite: (tenantId: string, token: string) =>
    request<void>('DELETE', `/api/invites/${tenantId}/${token}`),

  switchWorkspace: async (tenantId: string) => {
    const next = await request<AuthResponse>(
      'POST',
      `/api/auth/switch-workspace?tenantId=${encodeURIComponent(tenantId)}`,
    );
    storeAuth(next);
    return next;
  },

  listConversations: () => request<Conversation[]>('GET', '/api/conversations'),

  getConversation: (id: string) => request<Conversation>('GET', `/api/conversations/${id}`),

  createDm: (userId: string) => request<Conversation>('POST', '/api/conversations/dm', { userId }),

  createGroup: (name: string, memberIds: string[]) =>
    request<Conversation>('POST', '/api/conversations/group', { name, memberIds }),

  getMessages: (conversationId: string, before?: number | null, limit = 30) => {
    const params = new URLSearchParams();
    if (before != null) params.set('before', String(before));
    params.set('limit', String(limit));
    return request<MessagePage>('GET', `/api/conversations/${conversationId}/messages?${params}`);
  },

  sendMessage: (
    conversationId: string,
    content: string,
    clientMessageId: string,
    replyToId?: string | null,
  ) =>
    request<ChatMessage>('POST', `/api/conversations/${conversationId}/messages`, {
      content,
      clientMessageId,
      replyToId: replyToId ?? null,
    }),

  renameConversation: (conversationId: string, name: string) =>
    request<Conversation>('PATCH', `/api/conversations/${conversationId}/name`, { name }),

  addConversationMember: (conversationId: string, userId: string) =>
    request<Conversation>('POST', `/api/conversations/${conversationId}/members`, { userId }),

  removeConversationMember: (conversationId: string, userId: string) =>
    request<Conversation>('DELETE', `/api/conversations/${conversationId}/members/${userId}`),

  transferOwnership: (conversationId: string, userId: string) =>
    request<Conversation>('POST', `/api/conversations/${conversationId}/transfer`, { userId }),

  leaveConversation: (conversationId: string) =>
    request<void>('POST', `/api/conversations/${conversationId}/leave`),

  deleteConversation: (conversationId: string) =>
    request<void>('DELETE', `/api/conversations/${conversationId}`),

  updateConversationSettings: (
    conversationId: string,
    settings: {
      pinned?: boolean;
      muted?: boolean;
      archived?: boolean;
      notificationLevel?: 'ALL' | 'MENTIONS' | 'NONE';
    },
  ) => request<ConversationMember>('PATCH', `/api/conversations/${conversationId}/settings`, settings),

  editMessage: (messageId: string, content: string) =>
    request<ChatMessage>('PATCH', `/api/messages/${messageId}`, { content }),

  deleteMessage: (messageId: string) => request<void>('DELETE', `/api/messages/${messageId}`),

  markRead: (conversationId: string, sequence?: number | null) =>
    request<void>('POST', `/api/conversations/${conversationId}/read`, {
      sequence: sequence ?? null,
    }),

  searchUsers: (query: string) =>
    request<UserProfile[]>('GET', `/api/users/search?query=${encodeURIComponent(query)}`),

  searchInConversation: (conversationId: string, query: string) =>
    request<ChatMessage[]>(
      'GET',
      `/api/conversations/${conversationId}/messages/search?query=${encodeURIComponent(query)}`,
    ),

  searchAllMessages: (query: string) =>
    request<ChatMessage[]>(`GET`, `/api/messages/search?query=${encodeURIComponent(query)}`),

  updateWorkspace: (tenantId: string, name: string, slug: string, description: string) =>
    request<{ id: string; name: string }>('PUT', `/api/workspaces/${tenantId}`, {
      name,
      slug,
      description,
    }),

  updateProfile: (displayName: string) =>
    request<UserProfile>('PUT', '/api/users/profile', { displayName }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/api/users/password', { currentPassword, newPassword }),

  deleteAccount: () => request<void>('DELETE', '/api/users/me'),

  listWebhooks: () =>
    request<{ id: string; url: string; events: string[]; active: boolean }[]>(
      'GET',
      '/api/integrations/webhooks',
    ),

  registerWebhook: (url: string, events: string[], secret?: string) =>
    request<{ id: string; url: string }>('POST', '/api/integrations/webhooks', {
      url,
      events,
      secret: secret ?? null,
    }),

  updateWebhook: (id: string, changes: { url?: string; events?: string[]; active?: boolean }) =>
    request<{ id: string; url: string }>('PUT', `/api/integrations/webhooks/${id}`, changes),

  deleteWebhook: (id: string) => request<void>('DELETE', `/api/integrations/webhooks/${id}`),

  getProfiles: (ids: string[]) =>
    request<UserProfile[]>('GET', `/api/users/profiles?${ids.map(id => `ids=${encodeURIComponent(id)}`).join('&')}`),
};
