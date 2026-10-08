export interface TenantInfo {
  id: string;
  name: string;
  slug: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  displayName: string;
  currentTenantId: string;
  currentTenantName: string;
  tenants: TenantInfo[];
}

export interface ConversationMember {
  userId: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
  leftAt: string | null;
  lastReadSequence: number;
  muted: boolean;
  archived: boolean;
  pinned: boolean;
  notificationLevel: string;
}

export interface Conversation {
  id: string;
  tenantId: string;
  type: 'DM' | 'GROUP';
  status: string;
  name: string | null;
  createdBy: string;
  directKey: string | null;
  lastMessageSequence: number;
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
  unreadCount: number;
  lastReadSequence: number | null;
  pinned: boolean;
  muted: boolean;
  archived: boolean;
  notificationLevel: string;
  members: ConversationMember[];
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  tenantId: string;
  senderId: string;
  clientMessageId: string | null;
  sequence: number;
  content: string | null;
  replyToId: string | null;
  createdAt: string;
  editedAt: string | null;
  edited: boolean;
  deleted: boolean;
  /** Client-only optimistic send state (never sent by the server). */
  status?: 'sending' | 'failed';
}

export interface MessagePage {
  messages: ChatMessage[];
  hasMore: boolean;
  nextCursor: number | null;
}

export interface UserProfile {
  id: string;
  username: string;
  displayName: string;
  status: string;
  lastSeenAt: string | null;
  createdAt: string;
}

export interface RealtimeEvent {
  type:
    | 'MESSAGE_CREATED'
    | 'MESSAGE_EDITED'
    | 'MESSAGE_DELETED'
    | 'READ_UPDATED'
    | 'MEMBER_ADDED'
    | 'MEMBER_REMOVED'
    | 'MEMBER_ROLE_CHANGED'
    | 'CONVERSATION_DELETED'
    | 'SYNC_COMPLETE';
  tenantId: string;
  conversationId: string;
  sequence: number | null;
  payload: unknown;
  timestamp: string;
}
