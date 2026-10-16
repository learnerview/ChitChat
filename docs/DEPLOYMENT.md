# Deployment

This document describes how to build and run ChitChat, and the operational caveats to understand
before exposing it to real traffic. ChitChat is **not production-hardened**; see
[`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md).

## Build

```powershell
mvn clean package
```

This produces a Spring Boot executable jar at `target/ChitChat-1.0.0.jar` (the
`spring-boot-maven-plugin` `repackage` goal). The frontend is separate:

```powershell
cd frontend
npm ci
npm run build      # emits static assets to frontend/dist
```

There is **no `Dockerfile`, container image, `docker-compose`, or CI pipeline** in the repository.

## Runtime requirements

- **JDK 17+** (built and run with Java 17).
- **MongoDB** reachable via `MONGODB_URI`. Single-node is supported; no replica set is required.
  There is no embedded database and no automatic database provisioning.
- **`JWT_SECRET`** set to a strong random value (≥ 32 chars).
- Network **egress** from the app to your webhook subscribers' public URLs (the app makes outbound
  HTTP calls).

## Run

```powershell
$env:JWT_SECRET   = "<strong-random-secret>"
$env:MONGODB_URI  = "mongodb://mongo-host:27017/chitchat"
$env:ALLOWED_ORIGINS = "https://app.example.com"
java -jar target/ChitChat-1.0.0.jar
```

Or with Maven during development (`.\start_server.ps1` wraps this and reads `.env`):

```powershell
mvn spring-boot:run
```

Configuration reference: [`CONFIGURATION.md`](CONFIGURATION.md).

## MongoDB setup

- The application creates indexes at startup when `MONGODB_AUTO_INDEX=true` (default). For controlled
  environments set it to `false` and provision indexes out-of-band (see the index table in
  [`ARCHITECTURE.md`](ARCHITECTURE.md#data-model-mongodb-collections)).
- **Legacy data:** if upgrading from the pre-redesign schema, run the migration **before** deploying
  the new version:
  ```powershell
  mongosh "<MONGODB_URI>" scripts/migrate-v2.js
  ```
  The script is idempotent and prints a report; resolve any reported duplicate-DM conflicts first.
  New/empty databases do not need it.
- Configure backups and retention for the `chitchat` database. Webhook delivery records accumulate in
  `webhook_deliveries`; there is no built-in pruning job.

## Reverse proxy / TLS

Terminate TLS at a reverse proxy (nginx, etc.) and forward to the app. Required details:

- Forward the `Authorization` header and preserve query strings (the WebSocket `?ticket=` and
  `?tenantId=` parameters).
- Proxy the WebSocket upgrade for `/ws` (`Upgrade`/`Connection` headers) and the SockJS transport
  paths. Example nginx location:
  ```nginx
  location /ws {
      proxy_pass http://127.0.0.1:8080;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_read_timeout 3600s;
  }
  ```
- The app enables HTTP/2 (`server.http2.enabled=true`) and response compression; ensure the proxy
  does not double-compress or break upgrade semantics.
- Set `ALLOWED_ORIGINS` to your real frontend origin(s); the default `*` is development-only.

## Health & operations

- **No actuator / health endpoint** is included. Use process liveness and log output for now; add
  `spring-boot-starter-actuator` if you need `/actuator/health` and metrics.
- Logs go to stdout via SLF4J (root `INFO` by default). Collect them with your platform's log driver;
  there are no metrics or tracing exporters.
- The webhook worker, WebSocket heartbeat scheduler and request handling run in one JVM.

## Scaling

- **Webhook worker:** multi-instance safe — deliveries are claimed with an atomic lease, so running
  several instances does not double-deliver beyond at-least-once semantics.
- **Realtime:** **not** multi-instance safe out of the box. STOMP fanout uses the in-memory simple
  broker, the per-user connection registry is in-process, and WebSocket tickets are in-memory. To run
  more than one instance you must introduce an external broker behind `RealtimeEventPublisher` and a
  shared store for connection counts/tickets.
- **Sequences and idempotency** rely on atomic single-document updates and unique indexes, so they
  remain correct across instances.
- MongoDB is the shared source of truth; scale it according to your workload.

## Upgrade / rollback

- Deploy the new jar, then restart. There is no schema-version marker; the migration script is
  idempotent and safe to re-run.
- Index changes are additive; rolling back the application does not require removing indexes.

## Security checklist before exposure

- [ ] Strong `JWT_SECRET` provided (not the dev fallback).
- [ ] `ALLOWED_ORIGINS` set explicitly.
- [ ] TLS terminated by the proxy; security headers configured there.
- [ ] MongoDB network access restricted and authenticated; backups configured.
- [ ] External auth, if enabled, configured with signature verification **and** a secret.
- [ ] Outbound egress scoped as desired (webhooks are SSRF-guarded but still make outbound calls).
