import type { Conversation, UserProfile } from '../api/types';

/** The other participant in a DM, or null for groups/unresolvable keys. */
export function dmPeerId(conversation: Conversation, myUserId: string): string | null {
  return conversation.directKey?.split(':').find(id => id !== myUserId) ?? null;
}

/** Display title for a conversation: group name, peer name, or a fallback. */
export function conversationTitle(
  conversation: Conversation | null,
  profiles: Record<string, UserProfile>,
  myUserId: string,
): string {
  if (!conversation) return '';
  if (conversation.type === 'GROUP') return conversation.name ?? 'Group';
  const peerId = dmPeerId(conversation, myUserId);
  if (!peerId) return 'Direct message';
  return profiles[peerId]?.displayName ?? peerId.slice(0, 8);
}
