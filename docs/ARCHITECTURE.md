# Architecture

ChitChat is a **modular monolith**: a single deployable Spring Boot application organized into
packages that behave like modules. Each module owns its domain model, services, repositories and
DTOs, and interacts with other modules only through service interfaces — never by reaching into
another module's repositories.

This keeps deployment and data consistency simple (one process, one MongoDB) while preserving
boundaries that would make a future extraction to services mechanical.

## Module map

```text
com.learnerview.chitchat
├── ChitChatApplication            Spring Boot entry point
├── common
│   ├── error                      ErrorCode, ApiException, ErrorResponse, GlobalExceptionHandler
│   ├── security                   JwtTokenProvider, JwtAuthenticationFilter, JwtHandshakeHandler, WsTicketService
│   ├── tenancy                    TenantContext, TenantHeaderFilter, WebSocketTenantHandshakeInterceptor
│   └── event                      EventPublisherService (outbound integration events / webhooks)
├── authorization                  AuthorizationService (single gatekeeper)
├── auth                           SecurityConfig, AuthController, WsTicketController, MongoUserDetailsService
├── user                           User profile, password change, search
├── tenant                         Tenant, TenantMember, invite links, membership
├── conversation                   Conversation, ConversationMember
├── message                        Message, pagination, search, read state
├── realtime                       WebSocket/STOMP config, RealtimeEvent, publisher, connection registry
└── webhook                        Subscriptions, durable delivery worker, URL validator
```

### Dependency direction

- Controllers → module services → repositories.
- Any module that needs an access check calls `authorization.AuthorizationService`.
- Cross-module side effects (webhooks) go through `common.event.EventPublisherService`; realtime
  fanout goes through `realtime.RealtimeEventPublisher`. Both are interfaces with in-process
  implementations, so a broker/queue can be substituted behind them.

## Request lifecycle (HTTP)

1. `TenantHeaderFilter` — for tenant-scoped `/api/**` paths, requires `X-Tenant-Id` and puts it in
   `TenantContext`. Exempt: `/api/auth*`, `/api/workspaces*`, `/api/invites/accept`, `OPTIONS`, and
   `/ws/**`.
2. `JwtAuthenticationFilter` — resolves the bearer token to a userId. Internal tokens are validated
   with the ChitChat secret; the user must be `ACTIVE` and the `pwdAt` stamp must still match.
   External tokens are considered only if internal validation fails and external auth is enabled.
   For non-exempt paths the token's tenant claim must equal the header tenant.
3. Spring Security — `anyRequest().authenticated()` except the permitted matchers in `SecurityConfig`.
4. Controller → service. Services call `AuthorizationService` to resolve the caller and verify
   workspace/conversation membership and roles before touching data.
5. `GlobalExceptionHandler` renders `ApiException` and framework errors into the shared error shape.

## Request lifecycle (WebSocket / STOMP)

1. `ConnectionLimitHandshakeInterceptor` authenticates the upgrade with a single-use `?ticket=`
   (preferred for browsers) or an `Authorization: Bearer` token, and enforces
   `REALTIME_MAX_CONNECTIONS_PER_USER` (excess → `429`, missing/invalid credentials → `401`).
2. `WebSocketTenantHandshakeInterceptor` resolves the tenant from the ticket attribute, the
   `X-Tenant-Id` header, or a `tenantId` parameter.
3. `JwtHandshakeHandler` re-validates the tenant binding and that the user is still an active
   workspace member, then sets the principal to the userId.
4. `WebSocketSubscriptionInterceptor` authorizes each STOMP frame: destination allowlist
   (`/topic/conversations/{id}`, `/user/queue/sync`), conversation + workspace membership for
   topics, per-session subscription cap, and a SEND allowlist (`/app/conversations/{id}/resume`)
   with a payload-size limit.

## Data model (MongoDB collections)

| Collection | Key fields | Indexes |
|---|---|---|
| `users` | `username` (unique), `displayName`, `passwordHash`, `externalUserId`, `passwordChangedAt`, `status`, `createdAt` | `{username}` unique |
| `tenants` | `name`, `slug`, `ownerId`, `description`, `active`, `createdAt`, `updatedAt` | slug lookup |
| `tenant_members` | `tenantId`, `userId`, `role` (OWNER/ADMIN/MEMBER), `joinedAt`, `removedAt` (soft delete) | `{tenantId,userId}` unique, `{tenantId,role}` |
| `conversations` | `tenantId`, `type` (DM/GROUP), `name`, `status`, `createdBy`, `directKey`, `lastMessageSequence`, `lastMessageAt` | `{tenantId,directKey}` unique partial, `{tenantId,updatedAt}`, `{tenantId,status}` |
| `conversation_members` | `tenantId`, `conversationId`, `userId`, `role`, `joinedAt`, `leftAt` (soft delete), `lastReadSequence`, `muted`, `archived`, `pinned`, `notificationLevel` | `{conversationId,userId}` unique, `{tenantId,userId}` |
| `messages` | `tenantId`, `conversationId`, `senderId`, `clientMessageId`, `sequence`, `content`, `replyToId`, `createdAt`, `editedAt`, `deletedAt` | `{conversationId,sequence}` unique partial, `{tenantId,conversationId,sequence}`, `{conversationId,createdAt}`, `{tenantId,senderId,clientMessageId}` unique partial |
| `invite_links` | `tenantId`, `createdBy`, `token` (unique), `expiresAt`, `used`, `usedAt` | `{token}` unique, `{tenantId,createdAt}` |
| `webhook_subscriptions` | `tenantId`, `url`, `events`, `active`, `secret` | — |
| `webhook_deliveries` | `tenantId`, `subscriptionId`, `event`, `payload`, `status`, `attempts`, `nextAttemptAt`, `leaseUntil`, `lastError` | `{status,nextAttemptAt}`, `{tenantId,createdAt}` |

## Core contracts

**Ordering.** Every message is assigned a `sequence` allocated atomically on the conversation
document via a single-document `$inc` (`findAndModify` on `lastMessageSequence`). Ordering and
pagination are defined by `sequence`, never by timestamps. `conversation.lastMessageSequence` is the
high-water mark. Sequences are monotonic but **not gap-free** (a failed save after allocation
consumes a sequence).

**Idempotency.** Senders may attach a `clientMessageId`, unique per `(tenantId, senderId)`. A retry
with the same key returns the original message; the unique index arbitrates concurrent races, and a
key reused for different content returns `409 CLIENT_MESSAGE_ID_CONFLICT`.

**DM uniqueness.** Direct conversations store `directKey = sorted(userA, userB).join(":")`, unique
per tenant via a partial index. At most one DM exists per user pair per tenant; creation is
idempotent and reactivates soft-deleted memberships.

**Membership.** `conversation_members` holds one document per `(conversation, user)` with role,
join/leave state (`leftAt`), read cursor and preferences. Legacy `participantIds`/`readBy` fields
are gone (see the migration below).

**Read state.** `lastReadSequence` advances with `$max` only, so it is monotonic under concurrency.
Unread count = `lastMessageSequence - lastReadSequence` (floored at 0).

**Authorization.** `AuthorizationService` resolves the caller from the security context and verifies
workspace membership (`requireTenantMember`), workspace roles (`requireTenantRole`), conversation
membership (`requireConversationAccess`) and conversation roles (`requireConversationRole`). The JWT
`sub` claim is the userId; `authentication.getName()` is the userId everywhere.

**Soft deletes and cascades.**
- Removing a workspace member sets `tenant_members.removedAt` and cascade-sets `leftAt` on all of
  that user's conversation memberships in the workspace. Re-inviting revives the original row.
- The last workspace owner cannot be removed or leave.
- Deleting a workspace sets `tenants.active=false` (all access denied), deactivates its webhooks
  and deletes its invites.
- Deleting a conversation hard-deletes its messages and members, then the conversation document.
- Messages are soft-deleted (`deletedAt`); the API returns `content: null`, `deleted: true`.

**Compensating cleanup.** Multi-document writes fall back to best-effort compensation rather than
transactions: e.g. conversation members are written before the conversation document, and a failure
removes the orphans; workspace creation removes the workspace if the owner membership write fails;
invite acceptance releases its claim if membership creation fails.

## Realtime model

- The simple in-memory STOMP broker is enabled on `/topic` and `/queue`; the app destination prefix
  is `/app`.
- Events are typed `RealtimeEvent` envelopes (`type`, `tenantId`, `conversationId`, `sequence`,
  `payload`, `timestamp`). Types: `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`,
  `READ_UPDATED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_ROLE_CHANGED`, `CONVERSATION_DELETED`,
  and `SYNC_COMPLETE`.
- Resume: after subscribing to the conversation topic, the client sends its last-seen sequence to
  `/app/conversations/{id}/resume`; the server replays missed `MESSAGE_CREATED` events to
  `/user/queue/sync`, then a `SYNC_COMPLETE` marker. Clients dedupe by sequence.
- Fanout is best-effort: the write path never fails because a realtime publish failed.

## Webhooks / outbox

`WebhookEventPublisher` (an `EventPublisherService`) persists one `webhook_deliveries` record per
matching subscription — it performs no HTTP and its failures are swallowed so the originating write
is unaffected. `WebhookDeliveryWorker` polls (fixed delay), atomically claims records with a lease,
re-validates the URL against SSRF, POSTs with a 5 s timeout, and either marks `DELIVERED`, schedules
an exponential-backoff retry (`30s × 2^(attempts-1)`, capped at 30 min) or dead-letters as `FAILED`
after `app.webhook.max-attempts`. Leases let multiple instances run the worker; a crashed worker's
claim is recovered once the lease expires. Delivery is **at-least-once**.

## Technology

See the Tech Stack table in the [README](../README.md#tech-stack). Realtime uses Spring's STOMP
support with SockJS fallback; persistence is Spring Data MongoDB; auth is Spring Security + JJWT.
