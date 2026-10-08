/** Join-link helpers shared by the invite panel, settings, and join dialog. */

/** Full shareable link for a raw invite token. */
export function buildInviteLink(token: string): string {
  return `${window.location.origin}/invite/${token}`;
}

/** Accepts a raw token or a full join link (`…/invite/<token>`). */
export function extractInviteToken(value: string): string {
  const trimmed = value.trim();
  const match = trimmed.match(/\/invite\/([A-Za-z0-9_-]+)/);
  return match ? match[1] : trimmed;
}
