# ChitChat

ChitChat is a multi-tenant realtime messaging backend built with Spring Boot and MongoDB.

It handles authentication, workspaces, conversations, messaging, and realtime fanout through REST APIs and STOMP over WebSocket. The service is frontend-independent and can back web, mobile, or desktop clients.

## Features

 * **Authentication** - JWT bearer tokens (`sub` = userId), registration, login, workspace switching, external-token pass-through
* **Workspaces (tenancy)** - workspace CRUD, members, roles, invite links
* **Conversations** - DMs (uniqueness-enforced per tenant) and groups, membership management, ownership transfer
* **Messaging** - idempotent sends, monotonic per-conversation sequence ordering, cursor pagination, replies, edit, soft delete
 * **Read tracking** - per-member read cursors (`lastReadSequence`), unread counts, read state exposed on the conversation list
* **Realtime** - STOMP over WebSocket with typed event envelopes, authenticated handshake, subscription authorization
* **Webhooks** - subscribe to domain events, HMAC-signed delivery
* **Error model** - consistent JSON error responses with stable machine-readable codes

---

## Tech Stack

* Java 17, Spring Boot 3.2
* Spring Security (JWT), Spring WebSocket (STOMP)
* MongoDB (single-node compatible; transactions are not required)
* Maven

---

## Architecture

Modular monolith. Each package is a self-contained module with its own domain, services, repositories, and DTOs. Modules interact through service interfaces, not by reaching into each other's repositories.

The system intentionally remains a modular monolith: domain boundaries are enforced in code while deployment and data consistency remain simple. Consistency is achieved through atomic single-document updates and compensating cleanup rather than distributed transactions. Infrastructure such as distributed realtime fanout can be introduced behind existing interfaces (e.g. `RealtimeEventPublisher`) when horizontal scaling requires it.

```text
src/main/java/com/learnerview/chitchat/
  common/
    error/         - ErrorCode, ApiException, ErrorResponse, GlobalExceptionHandler
    security/      - JwtTokenProvider, JwtAuthenticationFilter, JwtHandshakeHandler
    tenancy/       - TenantContext, TenantHeaderFilter, WebSocketTenantHandshakeInterceptor
    event/         - EventPublisherService (webhook fanout)
  authorization/   - AuthorizationService (single gatekeeper for all access checks)
  auth/            - SecurityConfig, AuthController, MongoUserDetailsService, auth DTOs
  user/            - User profile, password change, search
  tenant/          - Workspaces, members, invite links
  conversation/    - Conversations + ConversationMember
  message/         - Messages, pagination, read state
  realtime/        - WebSocket config, RealtimeEvent envelope, event publisher
  webhook/         - Subscriptions and delivery
```

### Core contracts

**Ordering.** Every message gets a `sequence` allocated atomically on its conversation document (`$inc lastMessageSequence`). Ordering and pagination are defined by `sequence`, never by timestamps. `conversation.lastMessageSequence` is the high-water mark.

**Idempotency.** Senders attach a `clientMessageId` (unique per tenant + sender). A retry with the same key returns the original message instead of creating a duplicate; the unique index arbitrates races.

**DM uniqueness.** Direct conversations store a `directKey` (`sorted(userA, userB).join(":")`), unique per tenant via a partial index. Two DM conversations for the same user pair cannot exist in one tenant.

**Membership.** `conversation_members` holds one document per (conversation, user) with role, join/leave state, per-member read cursor, and preferences. Legacy `participantIds`/`readBy` are gone.

**Authorization.** Protected domain requests run through `AuthorizationService`, which resolves the caller from the security context, validates workspace membership, and validates conversation membership + role. The JWT `sub` claim is the userId; `auth.getName()` is the userId everywhere.

---

## Getting Started

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/chitchat` | Database connection |
| `MONGODB_AUTO_INDEX` | `true` | Create indexes at startup |
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | *(required)* | HMAC signing key |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime |
| `ALLOWED_ORIGINS` | `*` | CORS + WS origins (set explicitly in production) |
| `MAX_MESSAGE_LENGTH` | `5000` | Message content limit (chars) |
| `MAX_GROUP_MEMBERS` | `500` | Group size limit |
| `REALTIME_MAX_CONNECTIONS_PER_USER` | `5` | Concurrent WS connections per user (handshake `429`) |
| `REALTIME_MAX_SUBSCRIPTIONS_PER_SESSION` | `100` | STOMP subscriptions per session |
| `REALTIME_MAX_SEND_PAYLOAD_BYTES` | `65536` | Max SEND frame payload |
| `REALTIME_SYNC_BATCH_LIMIT` | `200` | Max replayed messages per resume |
| `WEBHOOK_POLL_INTERVAL_MS` | `5000` | Outbox worker poll interval |
| `WEBHOOK_MAX_ATTEMPTS` | `8` | Delivery attempts before dead-letter |
| `WEBHOOK_BATCH_SIZE` | `50` | Deliveries processed per poll |
| `WEBHOOK_LEASE_SECONDS` | `300` | Worker claim lease (multi-instance safety) |
| `WS_TICKET_TTL_SECONDS` | `60` | Single-use WebSocket ticket lifetime |

### Run

```bash
# 1. Migrate existing data FIRST (see Migration below)
mongosh "<MONGODB_URI>" scripts/migrate-v2.js

# 2. Start
mvn spring-boot:run
```

### Frontend

A React + Vite + TypeScript client lives in `frontend/`:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api and /ws to :8080
```

It covers the full chat flow: register/login, workspace switching (create/join via invite), DM/group creation with user search, cursor-paged history, realtime delivery with bounded backoff + manual reconnect and resume replay, right-click context menus everywhere (messages, conversations, workspace card, conversation header, members, invites, webhooks, search hits, profile cards), message search (conversation or global), read receipts, unread badges, profile management (display name, password, account), workspace settings (members, roles, invites, webhooks), and conversation settings (rename, members, transfer ownership, per-viewer pin/mute/archive, delete). Browser clients authenticate the WebSocket handshake with a short-lived single-use `?ticket=` (see WebSocket → Handshake), so the access JWT never appears in a URL.

### Tests

```bash
mvn test
```

---

# APIs

All endpoints are prefixed `/api`. Protected endpoints require:

```http
Authorization: Bearer <jwt>
X-Tenant-Id: <tenant-id>
```

## Authentication

### Register

`POST /api/auth/register`

```json
{ "username": "john", "displayName": "John Doe", "password": "secure_password" }
```

Creates the user, hashes the password with bcrypt, and provisions a default workspace where the user is `OWNER`. Returns `201`.

### Login

`POST /api/auth/login`

Optional header `X-Tenant-Id` selects the workspace; otherwise the default membership is used.

```json
{ "username": "john", "password": "secure_password" }
```

Response:

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

### Switch Workspace

`POST /api/auth/switch-workspace?tenantId=<tenant-id>`

Returns a fresh token scoped to the given workspace. `403` if the user is not a member.

### External Tokens

When `EXTERNAL_AUTH_ENABLED=true`, a JWT issued by an external identity provider is accepted directly on every request (see `JwtAuthenticationFilter`); no exchange round-trip is required. Internal ChitChat tokens are validated with the ChitChat secret.

---

## Workspaces

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/workspaces` | Create workspace; caller becomes `OWNER` |
| `PUT` | `/api/workspaces/{tenantId}` | Update (owner only) |
| `GET` | `/api/workspaces/{tenantId}/members` | List members |
| `DELETE` | `/api/workspaces/{tenantId}/members/{userId}` | Remove member (owner/admin) |
| `POST` | `/api/workspaces/{tenantId}/leave` | Leave workspace |
| `DELETE` | `/api/workspaces/{tenantId}` | Delete workspace (owner only) |

Member removal is a **soft delete** (`removedAt`) that cascades: all of the member's conversation memberships in the workspace are deactivated, REST/search/realtime access stops immediately, and re-inviting reinstates the original row. The last `OWNER` cannot be removed or leave — transfer ownership first. Deleting a workspace deactivates it for everyone (all API + realtime access denied), deactivates its webhooks, and revokes its invites.

Create request:

```json
{ "name": "Acme Corp", "slug": "acme-corp", "description": "Primary workspace" }
```

## Invites

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/invites/generate` | Generate token (owner/admin) |
| `GET` | `/api/invites/{tenantId}` | List active invites |
| `POST` | `/api/invites/accept` | Join workspace via token |
| `DELETE` | `/api/invites/{tenantId}/{token}` | Revoke an invite |

Invites always expire: default 7 days, hard cap 365 days (`expiresInDays`). Tokens are single-use and tenant-bound — revoking verifies the token belongs to the caller's workspace. Accepting an invite into a deleted workspace is rejected; a previously removed member who re-joins has their original membership row reinstated.

---

## Webhooks

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/integrations/webhooks` | Register endpoint (owner/admin) |
| `GET` | `/api/integrations/webhooks` | List endpoints |
| `PUT` | `/api/integrations/webhooks/{id}` | Update URL/events/active (owner/admin) |
| `DELETE` | `/api/integrations/webhooks/{id}` | Delete endpoint (owner/admin) |

```json
{ "url": "https://example.com/hooks/chitchat", "events": ["message.sent"], "secret": "optional-hmac-secret" }
```

**URL validation (SSRF protection).** Only `http`/`https` URLs that resolve to public IP addresses are accepted — loopback, private, link-local (e.g. cloud metadata `169.254.169.254`), CGNAT and reserved ranges are rejected. DNS is re-validated at every delivery.

**Durable delivery (outbox).** Events are first persisted to `webhook_deliveries`, then delivered by a background worker — queued deliveries survive application restarts. Delivery is **at-least-once**: consumers should deduplicate using `X-ChitChat-Delivery-Id`. Failed deliveries retry with exponential backoff (30s × 2^attempts, capped at 30 minutes); after `WEBHOOK_MAX_ATTEMPTS` (default 8) the delivery is dead-lettered as `FAILED`. Workers claim deliveries atomically with a lease, so multiple application instances can run the worker without double-delivery; a crashed worker's claims are recovered after the lease expires.

**Delivery headers:**

| Header | Purpose |
|---|---|
| `X-ChitChat-Event` | Event name (e.g. `message.sent`) |
| `X-ChitChat-Delivery-Id` | Unique delivery ID (for dedup) |
| `X-ChitChat-Timestamp` | Delivery timestamp |
| `X-ChitChat-Signature` | `sha256=` HMAC-SHA256 of `timestamp + "." + body` (only when a secret is set) |

Verify the signature against the raw body to prevent spoofing and replay.

---

## Conversations

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/conversations` | My conversations (with unread counts) |
| `POST` | `/api/conversations/dm` | Create/find DM |
| `POST` | `/api/conversations/group` | Create group |
| `GET` | `/api/conversations/{id}` | Conversation detail |
| `PATCH` | `/api/conversations/{id}/name` | Rename group (owner) |
| `POST` | `/api/conversations/{id}/members` | Add member (owner/admin) |
| `DELETE` | `/api/conversations/{id}/members/{userId}` | Remove member (owner/admin) |
| `POST` | `/api/conversations/{id}/leave` | Leave conversation |
| `POST` | `/api/conversations/{id}/transfer` | Transfer ownership (owner) |
| `DELETE` | `/api/conversations/{id}` | Delete conversation (DM: either member; group: owner) |
| `PATCH` | `/api/conversations/{id}/settings` | Viewer-scoped pinned/muted/archived/notification level |

### Create DM

`POST /api/conversations/dm`

```json
{ "userId": "u_456" }
```

Returns `201`. Existing DMs are reused; a duplicate pair cannot be created (uniqueness-enforced). Self-DMs are rejected.

### Create Group

`POST /api/conversations/group`

```json
{ "name": "Project Team", "memberIds": ["u_1", "u_2", "u_3"] }
```

Caller becomes `OWNER`. Returns `201`.

### Conversation response

```json
{
  "id": "c_1",
  "tenantId": "t_1",
  "type": "GROUP",
  "status": "ACTIVE",
  "name": "Project Team",
  "createdBy": "u_1",
  "directKey": null,
  "lastMessageSequence": 42,
  "lastMessageAt": "2026-10-07T09:15:00",
  "createdAt": "2026-10-01T10:00:00",
  "updatedAt": "2026-10-07T09:15:00",
  "unreadCount": 3,
  "lastReadSequence": 39,
  "members": [ { "userId": "u_1", "role": "OWNER", "lastReadSequence": 39, "muted": false } ]
}
```

### Business rules

* Owners must transfer ownership before leaving or being removed
* Owners/admins manage membership; only the owner renames, transfers, or deletes
* DMs require two distinct users

---

## Messages

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/conversations/{id}/messages` | Send message (idempotent) |
| `GET` | `/api/conversations/{id}/messages` | Cursor-paginated history |
| `GET` | `/api/conversations/{id}/messages/search?q=` | Search inside a conversation |
| `GET` | `/api/messages/search?q=` | Search across my conversations |
| `PATCH` | `/api/messages/{messageId}` | Edit (sender only) |
| `DELETE` | `/api/messages/{messageId}` | Soft delete (sender or owner) |
| `POST` | `/api/conversations/{id}/read` | Advance my read cursor |

### Send

```json
{
  "content": "Hello everyone",
  "replyToId": "optional-message-id",
  "clientMessageId": "client-generated-uuid"
}
```

* `clientMessageId` - retries with the same key return the original message (same `id`, same `sequence`, no duplicate); a key reused for *different* content returns `409`
* Content is limited to `MAX_MESSAGE_LENGTH` characters (`413` beyond)
* Reply targets must belong to the same conversation

### History (cursor pagination)

`GET /api/conversations/{id}/messages?before=<sequence>&after=<sequence>&limit=50`

* Default: newest first, `limit` default 50 (max 100)
* `before` - messages with `sequence < before`, newest first (page *back* in history)
* `after` - messages with `sequence > after`, oldest first (live catch-up)
* `before` and `after` are mutually exclusive (`400 INVALID_CURSOR`)

Response:

```json
{
  "messages": [
    { "id": "m_1", "sequence": 42, "content": "Hello", "senderId": "u_1",
      "clientMessageId": "uuid-1", "createdAt": "2026-10-07T09:15:00",
      "editedAt": null, "edited": false, "deleted": false }
  ],
  "hasMore": true,
  "nextCursor": 42
}
```

Pass `nextCursor` back as `before` to continue paging.

### Read state

`POST /api/conversations/{id}/read` with `{"sequence": 42}` (optional; body omitted = mark up to latest). The cursor only moves forward. The current read cursor and unread count are exposed on each conversation in `GET /api/conversations` as `lastReadSequence`, `latestSequence`, and `unreadCount`.

### Message response

Soft-deleted messages return `content: null` with `deleted: true`. Edited messages carry `editedAt`.

---

## WebSocket

Real-time messaging uses STOMP over WebSocket with SockJS fallback.

### Handshake

`/ws`

```http
Authorization: Bearer <jwt>
X-Tenant-Id: <tenant-id>
```

Browsers that cannot set headers authenticate with a **single-use ticket** instead of the JWT: `POST /api/auth/ws-ticket` (authenticated) returns a 60-second ticket bound to the caller's userId and tenant, then connect with `?ticket=<ticket>`. Tickets are consumed on use — reconnects fetch a fresh one. Non-browser clients may use the `Authorization: Bearer` header directly. The handshake is rejected with `401` when the ticket/token is missing or invalid. The principal is the userId; the token's tenant must match the header, and the user must still be an active member of the workspace.

The handshake is also rate-limited: a user may hold at most `REALTIME_MAX_CONNECTIONS_PER_USER` (default 5) concurrent connections; beyond that the handshake returns `429`.

### Subscribe

```
/topic/conversations/{conversationId}   # conversation fanout
/user/queue/sync                        # private sync stream (resume replays, control events)
```

Subscription is rejected unless the caller is an active member of the conversation *and* the workspace. Both destinations are the complete allowlist — anything else is rejected with `403`. A session may hold at most `REALTIME_MAX_SUBSCRIPTIONS_PER_SESSION` (default 100) subscriptions; unsubscribing frees a slot.

### Publish

```
/app/conversations/{conversationId}/resume
```

The only SEND destination is the resume protocol (the message write path is REST: `POST /api/conversations/{id}/messages`). SEND payloads above `REALTIME_MAX_SEND_PAYLOAD_BYTES` (default 64 KB) are rejected.

### Resume (missed-message recovery)

Sequences are monotonically increasing per conversation, so a reconnecting client can recover every message after its last observed sequence:

1. Subscribe to `/topic/conversations/{id}` first (live events start flowing immediately).
2. Send `{"afterSequence": <last seen>}` to `/app/conversations/{id}/resume`.
3. The server replays missed `MESSAGE_CREATED` envelopes to your private `/user/queue/sync`, then follows with `SYNC_COMPLETE`.
4. Deduplicate: topic events and replay overlap — drop anything with `sequence <= afterSequence`.

```json
{ "afterSequence": 41 }
```

`SYNC_COMPLETE` payload:

```json
{ "afterSequence": 41, "delivered": 3, "latestSequence": 44, "hasMore": false }
```

`hasMore: true` means the batch limit (`REALTIME_SYNC_BATCH_LIMIT`) was hit — resume again with `afterSequence = sequence` of the last replayed message. Sending no/`null` position skips the replay and only reports the latest sequence. Replay and completion both arrive on `/user/queue/sync`.

### Event envelope

Everything on the topic arrives as a typed envelope; switch on `type`:

```json
{
  "type": "MESSAGE_CREATED",
  "tenantId": "t_1",
  "conversationId": "c_1",
  "sequence": 42,
  "payload": { "id": "m_1", "senderId": "u_1", "content": "Hello" },
  "timestamp": "2026-10-07T09:15:00"
}
```

Types: `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `READ_UPDATED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_ROLE_CHANGED`, `CONVERSATION_DELETED` (topic); `MESSAGE_CREATED` replays and `SYNC_COMPLETE` (user sync queue).

Realtime delivery is best-effort: the REST/WS write path never fails because fanout failed. Clients reconcile via the resume protocol above on reconnect.

---

## Multi-Tenancy

Every request carries `X-Tenant-Id`. Isolation is enforced at four layers:

1. `TenantHeaderFilter` rejects requests without the header
2. The JWT tenant claim must match the header (mismatch = `401`)
3. `AuthorizationService` verifies workspace membership before any domain access
4. Every repository query is scoped by `tenantId`, backed by compound indexes

---

## Security

* **Authentication** - HMAC-SHA512 JWTs; `sub` = userId; protected routes require a valid bearer token
* **Authorization** - `/api/auth/**` is public; everything else goes through `AuthorizationService`
* **WebSocket** - JWT + tenant validation at handshake (missing/invalid tokens rejected with `401`), per-user connection cap, destination allowlist, membership check per subscription (conversation *and* workspace), subscription and payload limits
* **Revocation** - workspace member removal is a soft delete that cascades to all conversation memberships; REST, search and realtime subscribe all re-check membership, and new WS handshakes are denied
* **Passwords** - bcrypt-hashed (`passwordHash`), never returned by any endpoint
* **User directory** - user search/lookup is scoped to the caller's workspace; cross-tenant enumeration is not possible
* **Webhooks** - owner/admin only; SSRF-guarded URLs; signed, durable, retried delivery
* **CORS** - restricted via `ALLOWED_ORIGINS`

---

## Design decisions & known limitations

Deliberate tradeoffs, documented so they are not mistaken for oversights:

* **Token lifecycle** - access tokens live 24h (configurable). Disabled accounts and password changes invalidate existing tokens immediately (status + `pwdAt` stamp checks). Refresh tokens are a planned addition, not yet implemented.
* **WebSocket tickets** - single-use, 60s, held in memory (single-node by design; a shared store is only needed for multi-instance deployment). This avoids placing the long-lived JWT in handshake URLs.
* **Webhook delivery** - delivery is durable and retried (outbox + lease + backoff + dead-letter), but the *enqueue* is best-effort: it is not transactionally coupled to the originating write (that would require Mongo replica-set transactions, deliberately avoided). A process dying between the write and the enqueue can lose that one event.
* **Realtime** - single-broker fanout behind `RealtimeEventPublisher`; horizontal scaling introduces a broker (e.g. Redis pub/sub) behind that interface.
* **Frontend token storage** - the JWT lives in `localStorage` (standard SPA tradeoff; React escaping mitigates XSS, but a cookie-based session would be stronger).
* **Sequence gaps** - sequences are monotonic but not gap-free (a failed save after allocation consumes a sequence). Consumers must not assume contiguity.

---

## Error Model

All errors share one shape:

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
| `404` | `USER_NOT_FOUND`, `CONVERSATION_NOT_FOUND`, `MESSAGE_NOT_FOUND`, `WORKSPACE_NOT_FOUND` |
| `409` | `CONFLICT`, `USERNAME_TAKEN`, `SLUG_TAKEN`, `ALREADY_MEMBER`, `CLIENT_MESSAGE_ID_CONFLICT` |
| `413` | `PAYLOAD_TOO_LARGE`, `MESSAGE_TOO_LONG` |
| `500` | `INTERNAL_ERROR` |

---

## Database Collections

### users

Global identity. `username` unique.

* `id`, `username`, `displayName`, `passwordHash`, `email`, `externalUserId`, `status`, `createdAt`

### tenants / tenant_members

Workspace definitions and memberships (`role`: OWNER, ADMIN, MEMBER). Unique on `{tenantId, userId}`. Removed members keep their row with `removedAt` set (soft delete — audit trail + reinstatement). `tenants.active = false` marks a deleted workspace; it is enforced on every membership check.

### conversations

* `tenantId`, `type` (DM | GROUP), `name`, `status`, `createdBy`, `createdAt`, `updatedAt`
* `directKey` - sorted user pair for DMs; unique per tenant (partial index)
* `lastMessageSequence` - monotonic ordering primitive; `lastMessageAt`

Indexes: `{tenantId, directKey}` unique (partial), `{tenantId, updatedAt}`, `{tenantId, status}`

### conversation_members

One document per (conversation, user):

* `tenantId`, `conversationId`, `userId`, `role` (OWNER | ADMIN | MEMBER), `joinedAt`, `leftAt`
* `lastReadSequence` - read cursor (advances via `$max` only)
* `muted`, `archived`, `pinned`, `notificationLevel` (ALL | MENTIONS | NONE)

Indexes: `{conversationId, userId}` unique, `{tenantId, userId}`

### messages

* `tenantId`, `conversationId`, `senderId`, `clientMessageId`, `sequence`, `content`, `replyToId`
* `createdAt`, `editedAt`, `deletedAt` (soft delete)

Indexes: `{conversationId, sequence}` unique (partial), `{tenantId, conversationId, sequence}`, `{conversationId, createdAt}`, `{tenantId, senderId, clientMessageId}` unique (partial)

### invite_links / webhook_subscriptions / webhook_deliveries

Invite tokens with mandatory expiry (single-use). Webhook endpoints with event sets and HMAC secrets. `webhook_deliveries` is the durable outbox: `{status, nextAttemptAt}` indexed for the retry worker; statuses `PENDING` → `DELIVERED` | `FAILED`.

---

## Migration (legacy data)

If you have data from the pre-redesign schema, run the migration **before** deploying this version:

```bash
mongosh "<MONGODB_URI>" scripts/migrate-v2.js
```

It backfills, in order:

1. `users.password` -> `users.passwordHash`
2. `conversation_members` documents from legacy `conversations.participantIds`
3. `directKey` for DM conversations (reports duplicate-DM conflicts for manual resolution)
4. `messages.sequence` (ordered by `createdAt`, `_id`) and `conversations.lastMessageSequence/lastMessageAt`
5. Removes legacy fields (`participantIds`, `readBy`, `edited`, `deleted`)

The script is idempotent and prints a report. Resolve any reported conflicts before deploying. New deployments with an empty database do not need it.

---

## Project Structure

```text
src/main/java/com/learnerview/chitchat/
  common/
    error/         ErrorCode, ApiException, ErrorResponse, GlobalExceptionHandler
    security/      JwtTokenProvider, JwtAuthenticationFilter, JwtHandshakeHandler
    tenancy/       TenantContext, TenantHeaderFilter, WebSocketTenantHandshakeInterceptor
    event/         EventPublisherService
  authorization/   AuthorizationService
  auth/            SecurityConfig, AuthController, MongoUserDetailsService + dto/
  user/            UserController, UserService(+Impl), User + dto/
  tenant/          TenantController, TenantService(+Impl), MembershipService(+Impl),
                   InviteController, InviteLinkService(+Impl) + repositories
  conversation/    ConversationController, ConversationService(+Impl),
                   Conversation, ConversationMember + repositories + dto/
  message/         MessageController, MessageService(+Impl), Message + repository + dto/
  realtime/        WebSocketConfig, RealtimeMessageController, WebSocketSubscriptionInterceptor,
                   RealtimeEventPublisher, StompRealtimeEventPublisher, RealtimeEvent,
                   RealtimeConnectionRegistry, ConnectionLimitHandshakeInterceptor, dto/ResumeRequest
  webhook/         WebhookController, WebhookService(+Impl), WebhookEventPublisherServiceImpl + dto/

src/test/java/com/learnerview/chitchat/
  authorization/   AuthorizationServiceTest
  conversation/    ConversationDirectKeyTest
  message/         MessageServiceImplTest

scripts/
  migrate-v2.js    legacy data migration
```

---

## License

MIT
