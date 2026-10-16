# API Reference

All HTTP endpoints are prefixed `/api`. Protected endpoints require both headers:

```http
Authorization: Bearer <jwt>
X-Tenant-Id: <tenant-id>
```

`X-Tenant-Id` is enforced by `TenantHeaderFilter` for tenant-scoped `/api/**` paths. Exempt:
`/api/auth*`, `/api/workspaces*`, `/api/invites/accept`, `OPTIONS`, and the `/ws` handshake.

Unless noted, request and response bodies are JSON. Timestamps are ISO-8601 local date-times.

## Error model

Every error shares one shape (produced by `GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-10-07T09:15:00Z",
  "status": 404,
  "code": "CONVERSATION_NOT_FOUND",
  "message": "Conversation not found",
  "path": "/api/conversations/c_9/messages"
}
```

| Status | Codes |
|---|---|
| `400` | `BAD_REQUEST`, `VALIDATION_FAILED`, `MISSING_TENANT`, `INVALID_CURSOR` |
| `401` | `UNAUTHORIZED` |
| `403` | `FORBIDDEN`, `TENANT_ACCESS_DENIED`, `INSUFFICIENT_ROLE`, `CONVERSATION_ACCESS_DENIED`, `INSUFFICIENT_CONVERSATION_ROLE` |
| `404` | `NOT_FOUND`, `USER_NOT_FOUND`, `CONVERSATION_NOT_FOUND`, `MESSAGE_NOT_FOUND`, `WORKSPACE_NOT_FOUND` |
| `409` | `CONFLICT`, `USERNAME_TAKEN`, `SLUG_TAKEN`, `ALREADY_MEMBER`, `CLIENT_MESSAGE_ID_CONFLICT` |
| `413` | `MESSAGE_TOO_LONG` |
| `405` | `METHOD_NOT_ALLOWED` |
| `415` | `UNSUPPORTED_MEDIA_TYPE` |
| `500` | `INTERNAL_ERROR` |

## Authentication — `/api/auth`

### `POST /api/auth/register` — public

```json
{ "username": "john", "displayName": "John Doe", "password": "password123" }
```

- `username`: 3–40 chars, `[a-zA-Z0-9_]+`.
- `displayName`: 1–80 chars.
- `password`: 8–100 chars.

Creates the user (bcrypt-hashed password) and a default workspace where the caller is `OWNER`.
Responds `201` with a plain-text body. If workspace creation fails, the account is rolled back.

### `POST /api/auth/login` — public

Optional header `X-Tenant-Id` selects the workspace; otherwise the first active membership is used.

```json
{ "username": "john", "password": "password123" }
```

`200`:

```json
{
  "token": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "userId": "u_123",
  "username": "john",
  "displayName": "John Doe",
  "currentTenantId": "60d5ec49...",
  "currentTenantName": "Acme Corp",
  "tenants": [
    { "id": "60d5ec49...", "name": "Acme Corp", "slug": "acme-corp", "role": "OWNER" }
  ]
}
```

`403` if the user has no active memberships or the requested tenant is not a membership.

### `POST /api/auth/switch-workspace?tenantId=<id>` — authenticated

Returns a fresh token scoped to the given workspace (same body as login). `401` for a missing/invalid
bearer token; `403` if the caller is not a member.

### `POST /api/auth/ws-ticket` — authenticated + `X-Tenant-Id`

Issues a short-lived single-use WebSocket ticket bound to the caller and tenant.

```json
{ "ticket": "uuid", "expiresIn": 60 }
```

Requires active membership in the header tenant. `401` if unauthenticated.

## Workspaces — `/api/workspaces`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/workspaces` | authenticated | Create workspace; caller becomes `OWNER` |
| `PUT` | `/api/workspaces/{tenantId}` | owner | Update name/slug/description |
| `GET` | `/api/workspaces/{tenantId}/members` | member | List active members |
| `DELETE` | `/api/workspaces/{tenantId}/members/{userId}` | owner | Remove member (soft delete + cascade) |
| `POST` | `/api/workspaces/{tenantId}/leave` | member (non-owner) | Leave workspace |
| `DELETE` | `/api/workspaces/{tenantId}` | owner | Delete workspace (soft, deactivates everything) |

Create/update request:

```json
{ "name": "Acme Corp", "slug": "acme-corp", "description": "Primary workspace" }
```

`name` 1–100; `slug` 3–50 lowercase alphanumeric/hyphen; `description` ≤ 500.

Notes:
- Member removal is a soft delete (`removedAt`) that cascade-deactivates the user's conversation
  memberships in the workspace. Re-inviting reinstates the original row.
- The last `OWNER` cannot be removed or leave; transfer first.
- Deleting a workspace sets it inactive (all API + realtime access denied), deactivates its webhooks
  and deletes its invites.
- Endpoints returning `void` (member removal, leave, delete) respond `200 OK` (not `204`).

## Invites — `/api/invites`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/invites/generate` | owner/admin | Generate a token |
| `GET` | `/api/invites/{tenantId}` | owner/admin | List non-expired invites |
| `POST` | `/api/invites/accept` | authenticated | Join the invite's workspace |
| `DELETE` | `/api/invites/{tenantId}/{token}` | owner | Revoke an invite |

Generate request/response:

```json
{ "tenantId": "t_1", "expiresInDays": 7 }
```

```json
{ "token": "uuid", "tenantId": "t_1", "createdBy": "u_1",
  "expiresAt": "2026-10-14T10:00:00", "createdAt": "2026-10-07T10:00:00" }
```

- `expiresInDays` is optional; default 7, hard cap 365.
- Invites are **single-use and atomic**: `acceptInvite` performs a conditional update
  (`used != true` → `used = true`) so exactly one concurrent caller wins; the loser gets `404`. If
  membership creation fails afterward, the claim is released and the token can be used again.
- Revoking verifies the token belongs to the caller's workspace.

Accept request/response:

```json
{ "token": "uuid" }
```

```json
{ "status": "joined", "tenantId": "t_1" }
```

## Conversations — `/api/conversations`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/conversations` | member | List my conversations (with unread counts + viewer settings) |
| `POST` | `/api/conversations/dm` | member | Create/find a DM |
| `POST` | `/api/conversations/group` | member | Create a group |
| `GET` | `/api/conversations/{id}` | member | Conversation detail |
| `PATCH` | `/api/conversations/{id}/name` | group owner | Rename |
| `POST` | `/api/conversations/{id}/members` | group owner | Add member |
| `DELETE` | `/api/conversations/{id}/members/{userId}` | group owner | Remove member |
| `POST` | `/api/conversations/{id}/leave` | member (non-owner) | Leave (`204`) |
| `POST` | `/api/conversations/{id}/transfer` | group owner | Transfer ownership |
| `DELETE` | `/api/conversations/{id}` | DM: member; group: owner | Delete (`204`) |
| `PATCH` | `/api/conversations/{id}/settings` | member | Update viewer-scoped settings |

Create DM `POST /api/conversations/dm` (`201`):

```json
{ "userId": "u_456" }
```

Existing DMs are reused; a duplicate pair cannot be created; self-DMs are rejected; the target must
be an active workspace member.

Create group `POST /api/conversations/group` (`201`):

```json
{ "name": "Project Team", "memberIds": ["u_1", "u_2"] }
```

`name` ≤ 100. Caller becomes `OWNER`; size is capped at `MAX_GROUP_MEMBERS`.

Conversation response (detail):

```json
{
  "id": "c_1", "tenantId": "t_1", "type": "GROUP", "status": "ACTIVE",
  "name": "Project Team", "createdBy": "u_1", "directKey": null,
  "lastMessageSequence": 42, "lastMessageAt": "2026-10-07T09:15:00",
  "createdAt": "2026-10-01T10:00:00", "updatedAt": "2026-10-07T09:15:00",
  "unreadCount": 3, "lastReadSequence": 39,
  "pinned": false, "muted": false, "archived": false, "notificationLevel": "ALL",
  "members": [
    { "userId": "u_1", "role": "OWNER", "joinedAt": "2026-10-01T10:00:00",
      "leftAt": null, "lastReadSequence": 39, "muted": false,
      "archived": false, "pinned": false, "notificationLevel": "ALL" }
  ]
}
```

Update settings `PATCH /api/conversations/{id}/settings` (viewer-scoped; at least one field required,
otherwise `400`):

```json
{ "pinned": true, "muted": false, "archived": false, "notificationLevel": "MENTIONS" }
```

`notificationLevel` ∈ `ALL` | `MENTIONS` | `NONE`. Returns the caller's member record.

Business rules: owners/admins manage membership; only the owner renames/transfers/deletes a group;
DMs require two distinct active workspace members.

## Messages — `/api`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/conversations/{id}/messages` | Send (`201`, idempotent) |
| `GET` | `/api/conversations/{id}/messages` | Cursor-paginated history |
| `GET` | `/api/conversations/{id}/messages/search?query=` | Search within a conversation |
| `GET` | `/api/messages/search?query=` | Search across my conversations |
| `PATCH` | `/api/messages/{messageId}` | Edit (sender only) |
| `DELETE` | `/api/messages/{messageId}` | Soft delete (sender or group owner) (`204`) |
| `POST` | `/api/conversations/{id}/read` | Advance my read cursor (`204`) |

> **Search parameter is `query`, not `q`.**

Send:

```json
{ "content": "Hello everyone", "replyToId": "optional-message-id",
  "clientMessageId": "client-generated-uuid" }
```

- `clientMessageId` (≤ 64) is unique per `(tenant, sender)`: retries return the original message; a
  key reused with different content/conversation/reply target returns `409 CLIENT_MESSAGE_ID_CONFLICT`.
- Content is trimmed, required, and capped at `MAX_MESSAGE_LENGTH` chars (`413 MESSAGE_TOO_LONG`).
- `replyToId` (≤ 64) must reference a message in the same conversation (`404`/`400` otherwise).

History:

```
GET /api/conversations/{id}/messages?before=<seq>&after=<seq>&limit=50
```

- Default newest-first, `limit` default 50 (max 100).
- `before` → `sequence < before`, newest-first (page back).
- `after` → `sequence > after`, oldest-first (catch up).
- `before` and `after` together → `400 INVALID_CURSOR`.
- Response `nextCursor` is passed back as `before` to continue paging.

```json
{
  "messages": [
    { "id": "m_1", "conversationId": "c_1", "tenantId": "t_1", "senderId": "u_1",
      "clientMessageId": "uuid-1", "sequence": 42, "content": "Hello",
      "replyToId": null, "createdAt": "2026-10-07T09:15:00",
      "editedAt": null, "edited": false, "deleted": false }
  ],
  "hasMore": true,
  "nextCursor": 42
}
```

Read state:

```
POST /api/conversations/{id}/read          # mark up to latest
POST /api/conversations/{id}/read  { "sequence": 42 }   # mark up to a sequence
```

The cursor never moves backward (`$max`). Edit requires being the sender and the message not being
deleted; delete requires being the sender or the group owner (DMs have no owner concept, so either
member may delete). Soft-deleted messages return `content: null`, `deleted: true`.

## Users — `/api/users`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users/search?query=` | Search users in the caller's workspace |
| `GET` | `/api/users/profiles?ids=a&ids=b` | Fetch profiles for specific ids |
| `PUT` | `/api/users/profile` | Update display name |
| `POST` | `/api/users/password` | Change password |
| `DELETE` | `/api/users/me` | Delete account |

Update profile: `{ "displayName": "..." }` (≤ 80). Returns the updated profile.

Change password: `{ "currentPassword": "...", "newPassword": "..." }` (new: 8–100). Changing the
password invalidates previously issued tokens.

Profile response:

```json
{ "id": "u_1", "username": "john", "displayName": "John Doe",
  "status": "ACTIVE", "lastSeenAt": null, "createdAt": "2026-10-01T10:00:00" }
```

User search/lookup is scoped to the caller's workspace; cross-tenant enumeration is not possible.

## Webhooks — `/api/integrations/webhooks`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/integrations/webhooks` | owner/admin | Register (`201`) |
| `GET` | `/api/integrations/webhooks` | member | List |
| `PUT` | `/api/integrations/webhooks/{id}` | owner/admin | Update URL/events/active |
| `DELETE` | `/api/integrations/webhooks/{id}` | owner/admin | Delete (`204`) |

Register:

```json
{ "url": "https://example.com/hooks/chitchat", "events": ["message.sent"], "secret": "optional-hmac-secret" }
```

Response (secret is never returned):

```json
{ "id": "w_1", "url": "https://example.com/hooks/chitchat",
  "events": ["message.sent"], "active": true, "createdAt": "2026-10-07T09:15:00" }
```

- **SSRF guard.** Only `http`/`https` URLs whose host resolves exclusively to public addresses are
  accepted (loopback, private, link-local/metadata, CGNAT and reserved ranges rejected). DNS is
  re-validated at every delivery.
- **Durable, at-least-once delivery.** Each event is persisted to the outbox, then delivered by a
  background worker. Failed deliveries retry with exponential backoff (30 s × 2^(n−1), capped at
  30 min); after `WEBHOOK_MAX_ATTEMPTS` the delivery is dead-lettered as `FAILED`. Consumers should
  deduplicate on `X-ChitChat-Delivery-Id`.

Events emitted by the application: `conversation.created`, `message.sent`, `message.updated`,
`message.deleted`, `conversation.deleted`.

Delivery headers:

| Header | Purpose |
|---|---|
| `X-ChitChat-Event` | Event name (e.g. `message.sent`) |
| `X-ChitChat-Delivery-Id` | Unique delivery id (for dedup) |
| `X-ChitChat-Timestamp` | Delivery timestamp (ISO-8601) |
| `X-ChitChat-Signature` | `sha256=` HMAC-SHA256 of `timestamp + "." + body` (only when a secret is set) |

## WebSocket — `/ws`

STOMP over WebSocket with SockJS fallback. Authenticate the handshake with a bearer token
(non-browser) or `?ticket=<single-use-ticket>` (browsers). Missing/invalid credentials → `401`;
too many concurrent connections → `429`. Browser clients may also pass `?tenantId=` on the handshake.

Subscribe destinations (complete allowlist):
- `/topic/conversations/{conversationId}` — conversation fanout (requires active membership in the
  conversation *and* workspace).
- `/user/queue/sync` — private stream (resume replays, control events).

Send destination (complete allowlist):
- `/app/conversations/{conversationId}/resume` — resume protocol. Payload above
  `REALTIME_MAX_SEND_PAYLOAD_BYTES` is rejected.

Event envelope (switch on `type`):

```json
{
  "type": "MESSAGE_CREATED", "tenantId": "t_1", "conversationId": "c_1",
  "sequence": 42, "payload": { "id": "m_1", "senderId": "u_1", "content": "Hello" },
  "timestamp": "2026-10-07T09:15:00"
}
```

Topic types: `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `READ_UPDATED`, `MEMBER_ADDED`,
`MEMBER_REMOVED`, `MEMBER_ROLE_CHANGED`, `CONVERSATION_DELETED`.
Sync queue types: `MESSAGE_CREATED` (replays) and `SYNC_COMPLETE`.

Resume:

```json
{ "afterSequence": 41 }
```

The server replays missed `MESSAGE_CREATED` events to `/user/queue/sync`, then `SYNC_COMPLETE`:

```json
{ "afterSequence": 41, "delivered": 3, "latestSequence": 44, "hasMore": false }
```

`hasMore: true` means the batch limit (`REALTIME_SYNC_BATCH_LIMIT`) was reached — resume again with
`afterSequence = ` the last replayed sequence. A null/absent position skips replay and only reports
the latest sequence. Realtime delivery is best-effort; the write path never fails because fanout
failed.

## Multi-tenancy

Isolation is enforced at four layers:

1. `TenantHeaderFilter` requires `X-Tenant-Id` on tenant-scoped routes.
2. The token's tenant claim must match the header for those routes.
3. `AuthorizationService` verifies workspace (and conversation) membership before domain access.
4. Every repository query is scoped by `tenantId`, backed by compound indexes.
