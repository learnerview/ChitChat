# Production Readiness

An honest, evidence-based rubric for the **current** state of the codebase. Scores are deliberately
conservative: this project is a feature-complete engineering exercise, **not** a hardened production
system. Where a score is low, the gap is stated explicitly rather than excused.

**Overall: ~5.3 / 10** — suitable for demos, internal prototypes and further development; not yet
ready for untrusted production traffic.

| # | Dimension | Score | One-line verdict |
|---|---|---:|---|
| 1 | Build / CI reproducibility | 4/10 | Builds cleanly, but no CI, no wrapper, no container. |
| 2 | Automated tests | 6/10 | Strong service-layer units; no DB/E2E/WS/load tests. |
| 3 | Security | 6/10 | Solid core controls; missing rate limiting/revocation/audit. |
| 4 | Resilience / failure handling | 6/10 | Good atomic patterns; no transactions; best-effort edges. |
| 5 | Observability / logging | 3/10 | Logs only; no metrics, tracing, or health endpoint. |
| 6 | Config & secrets management | 5/10 | Env-driven and validated secret; unsafe defaults remain. |
| 7 | Error handling / API contracts | 6/10 | Consistent envelope; no OpenAPI; minor status quirks. |
| 8 | Performance / scale | 4/10 | Indexed & atomic, but single-node realtime; no load tests. |
| 9 | Code quality / static analysis | 5/10 | Clean modular code; no linter/formatter/coverage gates. |
| 10 | Deployment / ops | 4/10 | Runnable jar; no container/health/migration tooling. |
| 11 | Legal / licensing | 6/10 | MIT LICENSE present; no third-party inventory. |
| 12 | Documentation | 8/10 | Comprehensive, grounded docs; manual (no OpenAPI). |

---

## 1. Build / CI reproducibility — 4/10

**Evidence**
- `pom.xml` pins Java 17, Spring Boot 3.2.3 and all plugin/dependency versions; `mvn clean package`
  produces `target/ChitChat-1.0.0.jar`.
- Frontend has a lockfile (`frontend/package-lock.json`) and a `tsc -b && vite build` script.

**Gaps**
- No CI configuration (no `.github/`, no other pipeline files) — verification is manual.
- No Maven wrapper (`mvnw`), so the toolchain version is not pinned for contributors.
- No `Dockerfile`/image and no reproducible container build.
- No dependency lock for Maven beyond version pins; no SBOM.

## 2. Automated tests — 6/10

**Evidence**
- `mvn test`: **152 tests / 24 suites / 0 failures / 0 errors / 0 skipped** (verified locally).
- `npm test` (Vitest): **17 tests / 3 files**, all passing.
- Broad service coverage: `MessageServiceImplTest` (22), `ConversationServiceImplTest` (17),
  `WebSocketSubscriptionInterceptorTest` (12), `AuthorizationServiceTest` (8),
  `MembershipServiceImplTest` (8), webhook suites (22), plus a MockMvc security-chain test
  (`MessageSecurityIntegrationTest`, 7) and a context-load test.

**Gaps**
- MongoDB is always mocked; no test exercises real persistence, unique-index races, or atomic
  conditional updates against a database.
- No end-to-end/HTTP round-trip against a running server (the `test_saas_flow.ps1` script is manual
  and was not executed during this verification).
- No live WebSocket/STOMP integration test and no real outbound webhook delivery test.
- No coverage measurement or minimum-coverage gate.
- Some controllers (e.g. parts of auth/login edge cases) lack dedicated tests.

## 3. Security — 6/10

**Evidence**
- JWT (HS512) with tenant binding; `sub` = userId; disabled accounts and password changes invalidate
  tokens immediately (status + `pwdAt` checks in `JwtAuthenticationFilter`).
- Passwords bcrypt-hashed and never serialized; webhook secrets never returned by DTOs.
- Centralized authorization (`AuthorizationService`) with workspace/conversation membership + role
  checks; tenant isolation enforced by header + claim + per-query scoping.
- Webhook URLs SSRF-validated at registration and delivery (loopback/private/link-local/CGNAT/
  reserved rejected).
- External auth refuses to start without signature verification (fail-fast).
- No anonymous WebSocket sessions; single-use tickets keep the JWT out of URLs.

**Gaps**
- No REST rate limiting or login brute-force protection.
- No refresh tokens, no revocation list, no token rotation.
- `ALLOWED_ORIGINS` defaults to `*`.
- Frontend stores the JWT in `localStorage`.
- No security audit/pen-test, no dependency-vulnerability scanning, no CSP/security-header config.
- No account lockout, MFA, or audit log of sensitive actions.

## 4. Resilience / failure handling — 6/10

**Evidence**
- Single-document atomic operations for sequence allocation (`$inc`), read cursors (`$max`), message
  edit/delete, and invite claiming — no lost updates under concurrency.
- Idempotent sends; unique indexes arbitrate races; duplicate-key handling resolves to the winner.
- Compensating cleanup for partial multi-document writes (workspace/conversation creation, invite
  acceptance).
- Durable webhook outbox with leases, exponential backoff and dead-lettering; a crashed worker's
  claim is recovered after lease expiry.
- Realtime fanout is best-effort and never fails the write path.

**Gaps**
- Deliberately avoids MongoDB transactions, so there is a documented loss window between a write and
  its webhook enqueue (or between post and topic fanout).
- No retry/circuit-breaker policy around MongoDB; a DB outage surfaces as request failures.
- No dead-letter/requeue tooling or delivery dashboard.
- No chaos/failure-injection testing.

## 5. Observability / logging — 3/10

**Evidence**
- SLF4J logging with configurable root/app levels; noteworthy failures are logged (dead-lettered
  webhooks, publish failures, cleanup failures).

**Gaps**
- No metrics endpoint or Prometheus/OTel integration; no Spring Boot Actuator, so **no `/health`**.
- No distributed tracing, no correlation/request IDs, no structured (JSON) log format.
- No dashboards, alerts, or SLOs. Operationally, the service is a black box.

## 6. Config & secrets management — 5/10

**Evidence**
- All settings via environment variables mapped to Spring properties; a single required secret
  (`JWT_SECRET`) is validated at runtime and rejected if blank/placeholder.
- `.env` is gitignored; `.env.example` holds placeholders only; key material file types ignored.
- Startup fails fast on the unsafe external-auth combination.

**Gaps**
- Unvalidated numeric/string config can fail at startup with less-friendly errors.
- Unsafe defaults: `ALLOWED_ORIGINS=*`; `start_server.ps1` falls back to a known dev `JWT_SECRET`.
- No integration with a secret manager (Vault/AWS Secrets Manager/etc.).
- No config documentation for some non-env properties (compression, HTTP/2).

## 7. Error handling / API contracts — 6/10

**Evidence**
- Uniform error envelope with stable machine-readable `ErrorCode`s; a single
  `GlobalExceptionHandler` covers validation, type mismatch, unsupported method/media type, duplicate
  keys and an `INTERNAL_ERROR` fallback that does not leak internals.
- Explicit validation constraints on request DTOs.
- Documented contract in [`API.md`](API.md).

**Gaps**
- No generated OpenAPI/Swagger; the contract is hand-maintained and can drift.
- Inconsistent status semantics: several `void` workspace endpoints return `200` while conversation
  delete/leave return `204`.
- No API versioning strategy.
- Error format differs for filter-level failures (`TenantHeaderFilter` writes its own JSON).

## 8. Performance / scale — 4/10

**Evidence**
- Compound indexes support the hot read paths; cursor pagination uses the sequence index.
- Bounded fan-in: message/group size caps, connection/subscription/payload limits, sync batch limit,
  and a fixed search result cap (50).
- Sequence allocation is a single atomic document update (no global counter contention).

**Gaps**
- **No load, soak, or benchmark testing** of any kind; no published throughput/latency numbers.
- Realtime is single-instance (in-memory broker, registry and tickets); horizontal scaling needs new
  infrastructure.
- No caching layer; every read hits MongoDB.
- Search is `contains` substring matching (no text index) and listed toward the hot path.

## 9. Code quality / static analysis — 5/10

**Evidence**
- Clear module boundaries and consistent layering; authorization centralized; DTOs separate from
  entities; no repository cross-calls between modules.
- Small, readable services with targeted atomic updates.

**Gaps**
- No static analysis, linting, or formatting enforcement (no Checkstyle/SpotBugs/PMD; no frontend
  ESLint/Prettier config).
- No coverage threshold or quality gate.
- No Javadoc/API docs on most public types; Lombok reduces boilerplate but hides generated code.

## 10. Deployment / ops — 4/10

**Evidence**
- Produces a self-contained executable jar; env-var configuration; documented runbook and reverse
  proxy guidance in [`DEPLOYMENT.md`](DEPLOYMENT.md).
- Idempotent legacy migration script.

**Gaps**
- No containerization, no orchestration manifests, no CI/CD.
- No health/readiness probes (no actuator).
- No versioned migration tool (a single `mongosh` script).
- No built-in log shipping, backups, or outbox pruning.

## 11. Legal / licensing — 6/10

**Evidence**
- `LICENSE` is present and unambiguous (MIT, Copyright © 2026 SUMIT SHUKLA); the README references it.

**Gaps**
- No third-party license inventory / NOTICE file for the dependency set.
- No contributor license agreement or `CONTRIBUTING` legal terms beyond guidance.

## 12. Documentation — 8/10

**Evidence**
- README, `SECURITY.md` and the `docs/` set (`ARCHITECTURE`, `CONFIGURATION`, `API`, `DEPLOYMENT`,
  `PRODUCTION_READINESS`) are comprehensive and cross-linked.
- Every version, endpoint, config key and test number was re-derived from source; a
  [Groundedness & Verification](../README.md#groundedness--verification) section states scope and
  provenance, including the explicit "no AI/ML" note.

**Gaps**
- No generated OpenAPI document; the API reference is hand-maintained.
- No architecture diagrams; no changelog/release notes.
- Docs accuracy depends on manual upkeep.

---

## What would move the needle most

1. **CI + container image + health endpoint** — the largest single jump across dimensions 1, 5 and 10.
2. **Integration tests against a real MongoDB** and a runnable E2E suite (de-risks 2, 4, 8).
3. **REST rate limiting, refresh/revocation, and audit logging** (raises 3).
4. **Metrics/tracing and outbox observability** (raises 5, and ops confidence in 4/8).
5. **OpenAPI generation + static analysis/coverage gates** (raises 7, 9, 12).

## Performance Baseline (to establish)

No load/soak/benchmark tests currently exist in this repository. To establish a production
baseline, run one of the following against a deployed instance and record p50/p95/p99 latency
plus throughput:

- **HTTP API.** `wrk -t4 -c100 -d30s http://host:port/endpoint` or `hey -z 30s -c 100`.
- **Measurement points.** Submit endpoint throughput, read endpoint latency, WebSocket message
  fanout rate, and webhook delivery lag.
- **Record.** Store results in `docs/performance-baseline.md` (not committed to this repo yet) and
  re-run on every release to detect regressions.

