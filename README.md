# ChitChat

ChitChat is a headless, multi-tenant realtime messaging backend built with Spring Boot and MongoDB.

## Overview

ChitChat provides authentication, workspaces (tenancy), conversations, messaging and realtime
fanout through REST APIs and STOMP over WebSocket. It is frontend-independent and can back web,
mobile or desktop clients. A React + Vite + TypeScript reference client lives in `frontend/`.

The service is a modular monolith: each package is a self-contained module with its own domain,
services, repositories and DTOs, and modules interact through service interfaces rather than
reaching into each other's repositories. Consistency relies on atomic single-document updates and
compensating cleanup, not on distributed transactions.

> Status: this is a feature-complete engineering project, but it is **not production-hardened**.
> See [Limitations & Known Issues](#limitations--known-issues) and
> [`docs/PRODUCTION_READINESS.md`](docs/PRODUCTION_READINESS.md) before considering a deployment.

## Features

- **Authentication** — HS512 JWT bearer tokens (`sub` = userId), registration, login, workspace
  switching, password change, optional external-token acceptance (see
  [External tokens](#external-tokens)).
- **Workspaces (tenancy)** — workspace CRUD, members, roles (OWNER/ADMIN/MEMBER), single-use
  expiring invite links.
- **Conversations** — DMs (uniqueness-enforced per tenant) and groups, membership management,
  ownership transfer, per-viewer pin/mute/archive/notification level.
- **Messaging** — idempotent sends (`clientMessageId`), monotonic per-conversation sequence
  ordering, cursor pagination, replies, edit, soft delete.
- **Read tracking** — per-member read cursors (`lastReadSequence`), unread counts, read state
  exposed on the conversation list.
- **Realtime** — STOMP over WebSocket (with SockJS fallback), typed event envelopes, authenticated
  handshake (bearer JWT or single-use ticket), subscription authorization, resume/replay.
- **Webhooks** — durable outbox, HMAC-SHA256-signed delivery, exponential-backoff retries,
  dead-lettering, SSRF-guarded URLs.
- **Error model** — consistent JSON error responses with stable machine-readable codes.
- **Observability** — structured JSON/text logs via SLF4J; configurable log levels.

## Architecture

A modular monolith. Domain boundaries are enforced in code while deployment and data consistency
remain simple. Realtime fanout sits behind the `RealtimeEventPublisher` interface so a distributed
broker can be introduced when horizontal scaling requires it.

```text
src/main/java/com/learnerview/chitchat/
  common/
    error/         - ErrorCode, ApiException, ErrorResponse, GlobalExceptionHandler
    security/      - JwtTokenProvider, JwtAuthenticationFilter, JwtHandshakeHandler, WsTicketService
    tenancy/       - TenantContext, TenantHeaderFilter, WebSocketTenantHandshakeInterceptor
    event/         - EventPublisherService (webhook fanout)
  authorization/   - AuthorizationService (single gatekeeper for all access checks)
  auth/            - SecurityConfig, AuthController, WsTicketController, MongoUserDetailsService
  user/            - User profile, password change, search
  tenant/          - Workspaces, members, invite links
  conversation/    - Conversations + ConversationMember
  message/         - Messages, pagination, read state
  realtime/        - WebSocket config, RealtimeEvent envelope, event publisher, connection registry
  webhook/         - Subscriptions, delivery worker, URL validator
```

Full details, data model and core contracts: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Tech Stack

| Component | Version / notes |
|---|---|
| Java | 17 (`maven.compiler` source/target 17) |
| Spring Boot | 3.2.3 (`spring.boot.version`) |
| Spring Security | via `spring-boot-starter-security` (JWT filter, bcrypt) |
| Spring WebSocket | via `spring-boot-starter-websocket` (STOMP + SockJS) |
| Spring Data MongoDB | via `spring-boot-starter-data-mongodb` |
| Bean Validation | via `spring-boot-starter-validation` |
| JJWT | 0.11.5 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) |
| Lombok | 1.18.30 (compile-time only, `optional`) |
| JUnit | 5.8.2 (`junit-jupiter`) |
| Mockito | 4.11.0 |
| Maven | build via `spring-boot-maven-plugin` (`repackage`) + `maven-compiler-plugin` 3.11.0 |
| Frontend | React 18.3, Vite 5.4, TypeScript 5.6, `@stomp/stompjs` 7, `sockjs-client` 1.6, Vitest 2.1 |

## Prerequisites

- **JDK 17** (the build targets Java 17).
- **Maven 3.8+** (or use an IDE's bundled Maven).
- **MongoDB** reachable at the configured URI. The application connects to MongoDB at runtime;
  single-node MongoDB is supported (no replica set required). No embedded/auto-provisioned database
  is bundled.
- **Node.js 18+** and **npm** only if you want to run the React frontend.

## Quick Start

```powershell
# 1. Provide the required JWT secret (>= 32 chars). A .env file is read by start_server.ps1.
Copy-Item .env.example .env
#    then edit .env and set JWT_SECRET

# 2. Ensure MongoDB is running and reachable (default mongodb://localhost:27017/chitchat)

# 3. Start the backend (reads .env; falls back to a dev secret if JWT_SECRET is unset)
.\start_server.ps1
#    equivalent to: mvn spring-boot:run
```

The API is served at `http://localhost:8080`. See [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)
for every setting.

Frontend (optional):

```powershell
.\start_frontend.ps1     # or: cd frontend; npm install; npm run dev
# http://localhost:5173, proxies /api and /ws to http://localhost:8080
```

A manual end-to-end smoke script is available at `test_saas_flow.ps1` (requires the backend and
MongoDB to be running; it is **not** part of any automated CI).

## Configuration

All configuration is via environment variables (mapped in `src/main/resources/application.properties`).
`name` = environment variable, `property` = Spring property it maps to.

| Name | Purpose | Default | Required |
|---|---|---|---|
| `JWT_SECRET` | HMAC signing key for ChitChat JWTs (`app.jwtSecret`). Must be ≥ 32 chars and not blank. | *(none)* | **Yes** |
| `MONGODB_URI` | MongoDB connection URI (`spring.data.mongodb.uri`). | `mongodb://localhost:27017/chitchat` | No |
| `MONGODB_AUTO_INDEX` | Create MongoDB indexes at startup (`spring.data.mongodb.auto-index-creation`). | `true` | No |
| `SERVER_PORT` | HTTP port (`server.port`). | `8080` | No |
| `JWT_EXPIRATION_MS` | ChitChat access-token lifetime in ms (`app.jwtExpirationInMs`). | `86400000` (24 h) | No |
| `ALLOWED_ORIGINS` | Comma-separated CORS + WebSocket allowed origin patterns (`app.cors.allowed-origins`). | `*` | No (set explicitly in production) |
| `MAX_MESSAGE_LENGTH` | Max message content length in characters (`app.limits.max-message-length`). | `5000` | No |
| `MAX_GROUP_MEMBERS` | Max members per group conversation (`app.limits.max-group-members`). | `500` | No |
| `REALTIME_MAX_CONNECTIONS_PER_USER` | Concurrent WebSocket connections per user (`app.realtime.max-connections-per-user`). | `5` | No |
| `REALTIME_MAX_SUBSCRIPTIONS_PER_SESSION` | STOMP subscriptions per session (`app.realtime.max-subscriptions-per-session`). | `100` | No |
| `REALTIME_MAX_SEND_PAYLOAD_BYTES` | Max STOMP SEND frame payload (`app.realtime.max-send-payload-bytes`). | `65536` | No |
| `REALTIME_SYNC_BATCH_LIMIT` | Max messages replayed per resume request (`app.realtime.sync-batch-limit`). | `200` | No |
| `EXTERNAL_AUTH_ENABLED` | Accept externally-issued JWTs (`app.externalAuth.enabled`). | `false` | No |
| `EXTERNAL_AUTH_VERIFY_SIGNATURE` | Verify external JWT signatures (`app.externalAuth.verifySignature`). | `true` | No |
| `EXTERNAL_AUTH_JWT_SECRET` | Secret used to verify external JWTs (`app.externalAuth.jwtSecret`). | *(blank)* | No |
| `EXTERNAL_AUTH_USERNAME_CLAIM` | Claim used as the external username (`app.externalAuth.usernameClaim`). | `preferred_username` | No |
| `EXTERNAL_AUTH_USER_ID_CLAIM` | Claim used as the external user id (`app.externalAuth.userIdClaim`). | `sub` | No |
| `EXTERNAL_AUTH_AUTO_PROVISION_USER` | Auto-create a local user on first external login (`app.externalAuth.autoProvisionUser`). | `false` | No |
| `LOG_LEVEL_ROOT` | Root log level (`logging.level.root`). | `INFO` | No |
| `LOG_LEVEL_CHITCHAT` | Log level for `com.learnerview.chitchat` (`logging.level.com.learnerview.chitchat`). | `INFO` | No |
| `WEBHOOK_POLL_INTERVAL_MS` | Webhook outbox worker poll interval (`app.webhook.poll-interval-ms`). | `5000` | No |
| `WEBHOOK_POLL_INITIAL_DELAY_MS` | Worker initial delay after startup (`app.webhook.poll-initial-delay-ms`). | `10000` | No |
| `WEBHOOK_MAX_ATTEMPTS` | Delivery attempts before dead-letter (`app.webhook.max-attempts`). | `8` | No |
| `WEBHOOK_BATCH_SIZE` | Deliveries processed per poll (`app.webhook.batch-size`). | `50` | No |
| `WEBHOOK_LEASE_SECONDS` | Worker claim lease (`app.webhook.lease-seconds`). | `300` | No |
| `WS_TICKET_TTL_SECONDS` | WebSocket single-use ticket lifetime (`app.ws.ticket-ttl-seconds`). | `60` | No |

Full reference, including startup validation behaviour: [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md).

## Usage

All endpoints are prefixed `/api`. Protected requests require:

```http
Authorization: Bearer <jwt>
X-Tenant-Id: <tenant-id>
```

Register and log in (see [`docs/API.md`](docs/API.md) for the full contract):

```powershell
$base = "http://localhost:8080/api"
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" `
  -Body '{"username":"john","displayName":"John Doe","password":"password123"}'

$auth = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body '{"username":"john","password":"password123"}'
$token  = $auth.token
$tenant = $auth.currentTenantId
```

Create a group conversation and send a message:

```powershell
$headers = @{ Authorization = "Bearer $token"; "X-Tenant-Id" = $tenant }

$group = Invoke-RestMethod -Method Post -Uri "$base/conversations/group" -Headers $headers `
  -ContentType "application/json" -Body '{"name":"Project Team","memberIds":[]}'

Invoke-RestMethod -Method Post -Uri "$base/conversations/$($group.id)/messages" -Headers $headers `
  -ContentType "application/json" `
  -Body '{"content":"Hello everyone","clientMessageId":"uuid-1"}'
```

Search uses the `query` parameter (**not** `q`):

```powershell
Invoke-RestMethod -Uri "$base/messages/search?query=hello" -Headers $headers
```

Cursor-paginated history:

```powershell
Invoke-RestMethod -Uri "$base/conversations/$($group.id)/messages?before=100&limit=50" -Headers $headers
```

### External tokens

When `EXTERNAL_AUTH_ENABLED=true`, ChitChat accepts a JWT issued by an external identity provider.
Ground rules (from `JwtTokenProvider` and `JwtAuthenticationFilter`):

- The external token is only consulted when the token fails **internal** validation, so internal
  ChitChat tokens always take precedence.
- Signature verification **must** be enabled. If `EXTERNAL_AUTH_ENABLED=true` and
  `EXTERNAL_AUTH_VERIFY_SIGNATURE=false`, the application **fails fast at startup**
  (`@PostConstruct validateExternalAuthConfiguration()` throws `IllegalStateException`). Accepting
  external JWTs without signature verification would allow token forgery.
- A non-blank `EXTERNAL_AUTH_JWT_SECRET` is required for external tokens to validate. If enabled
  with signature verification but **no** secret, external tokens are silently rejected (there is no
  fail-fast for a missing secret).
- A resolved external username **and** a tenant context (`X-Tenant-Id`) are required. If
  `EXTERNAL_AUTH_AUTO_PROVISION_USER=false` (default) and no local user matches the username, the
  request is treated as unauthenticated and receives `401`.

## Testing

```powershell
mvn test          # backend: 152 tests across 24 suites (JUnit 5 / Mockito)
```

Backend tests are unit/`@WebMvcTest` tests with repositories mocked; they run without a live
MongoDB. Results and scope are recorded in
[Groundedness & Verification](#groundedness--verification).

```powershell
cd frontend
npm test          # frontend: 17 Vitest tests across 3 files
```

`test_saas_flow.ps1` is a manual E2E smoke script (backend + MongoDB required), not CI.

## Deployment

Build a runnable jar and run it with MongoDB and a real `JWT_SECRET`:

```powershell
mvn clean package
java -jar target/ChitChat-1.0.0.jar
```

No `Dockerfile`, container image, or CI pipeline is included in the repository. Operational
guidance (process, reverse proxy, WebSocket forwarding, scaling caveats, MongoDB) is in
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Limitations & Known Issues

Honest, current-state limitations. Deliberate tradeoffs are marked *(by design)*.

- **Not production-hardened.** There is no CI/CD, no containerization, no load/soak testing, no
  rate limiting on REST endpoints, and no metrics/tracing. See the rubric in
  [`docs/PRODUCTION_READINESS.md`](docs/PRODUCTION_READINESS.md).
- **MongoDB is required at runtime.** The app does not start a database and has no embedded
  fallback; indexes are created at startup unless `MONGODB_AUTO_INDEX=false`.
- **Single-node realtime is the default.** STOMP fanout uses the in-memory simple broker and the
  per-user connection registry is in-process; horizontal scaling needs an external broker behind
  `RealtimeEventPublisher` and a shared connection/ticket store.
- **WebSocket tickets are in-memory** (single-use, short TTL). They do not survive restarts and are
  not shared across instances.
- **Webhook enqueue is best-effort.** Delivery itself is durable/retried, but the outbox write is
  not transactionally coupled to the originating write (no Mongo transactions), so a crash between
  the two can lose a single event.
- **Token lifecycle.** Access tokens live 24 h by default. There are no refresh tokens and no
  server-side revocation list; disabled accounts and password changes are enforced per request via
  the user status and `pwdAt` stamp.
- **Sequence gaps.** Sequences are monotonic but not gap-free; consumers must not assume contiguity.
- **Search is `contains`-style.** Message search uses case-insensitive substring matching with a
  fixed result cap (50), not a full-text index.
- **`ALLOWED_ORIGINS` defaults to `*`.** Convenient for development, unsafe for production; set it
  explicitly.
- **Frontend JWT storage.** The reference client keeps the access token in `localStorage` (standard
  SPA tradeoff).
- **No OpenAPI/Swagger document** is generated; the API contract is maintained by hand in
  [`docs/API.md`](docs/API.md).
- **External auth secret is not fail-fast.** Enabling external auth with a missing secret does not
  stop startup; external tokens simply never validate.

## Groundedness & Verification

### Verification status

Verified in this repository at the time of writing:

- **Build/compile**: `mvn test` compiles the main and test sources with Java 17 / Spring Boot 3.2.3.
- **Automated backend tests**: **152 tests, 24 suites, 0 failures, 0 errors, 0 skipped**
  (`mvn test`, exit 0). These are unit tests and one MockMvc security integration test; MongoDB is
  mocked, so no live database was required.
- **Automated frontend tests**: **17 tests, 3 files, 0 failures** (`npm test` → `vitest run`).
- **Test scope highlights** (from `target/surefire-reports`): `MessageServiceImplTest` (22),
  `ConversationServiceImplTest` (17), `WebSocketSubscriptionInterceptorTest` (12),
  `AuthorizationServiceTest` (8), `MembershipServiceImplTest` (8), `Webhook*Test` (22 across four
  suites), `MessageSecurityIntegrationTest` (7, real filter chain with mocked services).

**Not verified** here:

- No test requires or exercises a live MongoDB; no integration test hits a real database,
  transaction, or index-uniqueness race at scale.
- No end-to-end/browser test, no WebSocket test against a live broker, no real HTTP webhook
  delivery, no performance/load test.
- `test_saas_flow.ps1` was **not executed** during this documentation pass (it needs a running
  backend + MongoDB). It was corrected to match the current contract but is unverified end-to-end.
- The frontend was not type-checked/built (`npm run build`) during this pass; only `npm test` ran.

### Data & evidence provenance

- This project has **no datasets, training data, or scraped inputs**. Its "data" is application
  state created at runtime by its own API (users, workspaces, conversations, messages) and stored
  in MongoDB.
- The webhook E2E example in this README targets `https://example.com/...` — a placeholder, not a
  live endpoint.
- Test evidence comes from executing `mvn test` and `npm test` locally; numbers above are copied
  from that run (`target/surefire-reports` and Vitest output).
- There are no external AI/model services. External auth, if enabled, delegates to an
  operator-supplied identity provider via a shared HMAC secret.

### AI-output grounding

**This project contains no AI/ML components.** There is no model inference, no LLM calls, no
embeddings, and no trained or synthetic data. Consequently, there are no AI-generated outputs to
ground; every behavior is deterministic application logic that can be traced to source code.

## Roadmap

Not committed to a schedule; these are the known next steps implied by the limitations above:

- CI pipeline (build + `mvn test` + `npm test`) and a container image.
- Refresh tokens and/or a revocation list.
- External broker for realtime fanout + shared ticket/connection store for multi-instance.
- Broader integration tests against a real MongoDB (including index/uniqueness races).
- REST rate limiting and basic metrics/health endpoints.
- OpenAPI document generation.

## Contributing

- Keep the module boundaries: controllers/services depend on `AuthorizationService`, never on each
  other's repositories.
- Add or update tests with every behavior change and run `mvn test` (and `cd frontend; npm test`)
  before opening a PR.
- Match existing code style; do not add comments unless they explain non-obvious intent.
- Never commit secrets or a real `.env` (it is gitignored; use `.env.example`).

## Security

See [`SECURITY.md`](SECURITY.md) for how secrets are handled and how to report a vulnerability.
Highlights: `/api/auth/ws-ticket` requires authentication; all other `/api/auth/**` routes are
public. Everything else is authenticated and authorized through `AuthorizationService`.
Passwords are bcrypt-hashed and never returned. Webhook URLs are SSRF-guarded and optionally
HMAC-signed. **No security audit has been performed**; treat the current state as unhardened.

## License

MIT. See [`LICENSE`](LICENSE) (Copyright © 2026 SUMIT SHUKLA).
