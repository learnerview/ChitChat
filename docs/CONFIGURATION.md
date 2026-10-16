# Configuration

ChitChat is configured entirely through environment variables, mapped in
`src/main/resources/application.properties`. There are no profiles or external config servers.

## How configuration is supplied

- The `start_server.ps1` helper writes each non-comment `KEY=VALUE` line from a local `.env` file
  into the process environment before starting Maven. `.env` is gitignored; `.env.example` documents
  the shape.
- If you start the app directly (`mvn spring-boot:run`, `java -jar ...`), export the variables
  yourself (shell environment, container env, etc.).
- If `JWT_SECRET` is unset, `start_server.ps1` falls back to a development secret
  (`dev-secret-key-at-least-32-characters-long`). **Never rely on this in production.**

## Required

| Variable | Property | Notes |
|---|---|---|
| `JWT_SECRET` | `app.jwtSecret` | No default (`${JWT_SECRET}`). Must be ≥ 32 characters and must not equal the placeholder `change-me-to-a-long-random-secret-key-minimum-32-chars`; otherwise startup/token generation fails with `IllegalStateException`. |

## Application

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `MONGODB_URI` | `spring.data.mongodb.uri` | `mongodb://localhost:27017/chitchat` | MongoDB connection string. Single-node is supported. |
| `MONGODB_AUTO_INDEX` | `spring.data.mongodb.auto-index-creation` | `true` | Create/ensure indexes on startup. Set `false` if an operator manages indexes. |
| `SERVER_PORT` | `server.port` | `8080` | HTTP port. |
| `JWT_EXPIRATION_MS` | `app.jwtExpirationInMs` | `86400000` | ChitChat access-token lifetime (24 h). |
| `ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `*` | CORS + WebSocket allowed origin patterns. Default `*` is dev-only. |
| `LOG_LEVEL_ROOT` | `logging.level.root` | `INFO` | Root log level. |
| `LOG_LEVEL_CHITCHAT` | `logging.level.com.learnerview.chitchat` | `INFO` | Application log level. |

## Limits

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `MAX_MESSAGE_LENGTH` | `app.limits.max-message-length` | `5000` | Max message content length (characters). Exceeding it returns `413 MESSAGE_TOO_LONG`. |
| `MAX_GROUP_MEMBERS` | `app.limits.max-group-members` | `500` | Max members per group conversation. |
| `REALTIME_MAX_CONNECTIONS_PER_USER` | `app.realtime.max-connections-per-user` | `5` | Concurrent WebSocket connections per user; handshake returns `429` beyond this. Soft limit (simultaneous handshakes may briefly exceed it). |
| `REALTIME_MAX_SUBSCRIPTIONS_PER_SESSION` | `app.realtime.max-subscriptions-per-session` | `100` | STOMP subscriptions per session (distinct destinations). |
| `REALTIME_MAX_SEND_PAYLOAD_BYTES` | `app.realtime.max-send-payload-bytes` | `65536` | Max STOMP SEND frame payload. |
| `REALTIME_SYNC_BATCH_LIMIT` | `app.realtime.sync-batch-limit` | `200` | Max messages replayed per resume request. |
| `WS_TICKET_TTL_SECONDS` | `app.ws.ticket-ttl-seconds` | `60` | Lifetime of a single-use WebSocket ticket. |

## External authentication

See [External tokens](../README.md#external-tokens) for the behavioral contract.

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `EXTERNAL_AUTH_ENABLED` | `app.externalAuth.enabled` | `false` | Accept externally-issued JWTs. |
| `EXTERNAL_AUTH_VERIFY_SIGNATURE` | `app.externalAuth.verifySignature` | `true` | Verify external JWT signatures. **Must stay `true` when external auth is enabled** — otherwise the app fails fast at startup. |
| `EXTERNAL_AUTH_JWT_SECRET` | `app.externalAuth.jwtSecret` | *(blank)* | Secret used to verify external JWTs. If blank, external tokens never validate. |
| `EXTERNAL_AUTH_USERNAME_CLAIM` | `app.externalAuth.usernameClaim` | `preferred_username` | Claim mapped to the local username. |
| `EXTERNAL_AUTH_USER_ID_CLAIM` | `app.externalAuth.userIdClaim` | `sub` | Claim mapped to the external user id. |
| `EXTERNAL_AUTH_AUTO_PROVISION_USER` | `app.externalAuth.autoProvisionUser` | `false` | Auto-create a local user on first external login. |

## Webhook outbox worker

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `WEBHOOK_POLL_INTERVAL_MS` | `app.webhook.poll-interval-ms` | `5000` | Fixed delay between worker polls. |
| `WEBHOOK_POLL_INITIAL_DELAY_MS` | `app.webhook.poll-initial-delay-ms` | `10000` | Delay before the first poll after startup. |
| `WEBHOOK_MAX_ATTEMPTS` | `app.webhook.max-attempts` | `8` | Attempts before a delivery is dead-lettered (`FAILED`). |
| `WEBHOOK_BATCH_SIZE` | `app.webhook.batch-size` | `50` | Deliveries processed per poll. |
| `WEBHOOK_LEASE_SECONDS` | `app.webhook.lease-seconds` | `300` | Claim lease; a crashed worker's claim is recovered after it expires. |

## Startup validation

- `JwtTokenProvider` derives HMAC key material from `JWT_SECRET` and throws if it is blank or equals
  the placeholder value.
- `JwtTokenProvider.validateExternalAuthConfiguration()` (`@PostConstruct`) throws
  `IllegalStateException` when `EXTERNAL_AUTH_ENABLED=true` **and**
  `EXTERNAL_AUTH_VERIFY_SIGNATURE=false`. There is no equivalent fail-fast for a missing external
  secret; instead, external tokens simply fail to validate.

## Notes

- `server.compression` is enabled for common text types and `server.http2.enabled=true` is set in
  `application.properties` (both non-configurable via env in the current build).
- The application does not start or manage MongoDB; point `MONGODB_URI` at a reachable instance.
