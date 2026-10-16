# Security Policy

## Reporting a vulnerability

If you believe you have found a security vulnerability in ChitChat, please report it privately:

1. Open a **private** security advisory on the project's repository host (e.g. GitHub →
   *Security* → *Report a vulnerability*), **or**
2. Contact the maintainer directly at the address associated with the repository owner.

Please include:

- A description of the issue and its impact.
- Reproduction steps or a proof of concept.
- Affected version/commit and configuration.
- Any suggested remediation.

**Please do not open a public issue for undisclosed vulnerabilities.** There is no formal SLA or
bug-bounty program; reports are handled on a best-effort basis.

## Supported versions

This is an unversioned engineering project (current version `1.0.0` per `pom.xml`). Only the latest
`main` is supported. There are no backported security releases.

## No audit performed

This project has **not** undergone a security audit or penetration test. It contains production-
oriented controls, but it must be treated as **not production-hardened**. See
[`docs/PRODUCTION_READINESS.md`](docs/PRODUCTION_READINESS.md) for the honest state of the project.

## How secrets are handled

- **JWT signing secret.** `JWT_SECRET` (`app.jwtSecret`) is required. `JwtTokenProvider` derives the
  HMAC key material with SHA-512 and rejects a blank secret or the placeholder
  `change-me-to-a-long-random-secret-key-minimum-32-chars` at runtime. The secret is read from the
  environment, never persisted by the application.
- **External auth secret.** `EXTERNAL_AUTH_JWT_SECRET` is optional and only used when external auth
  is enabled. If `EXTERNAL_AUTH_ENABLED=true` and `EXTERNAL_AUTH_VERIFY_SIGNATURE=false`, the
  application fails fast at startup (`validateExternalAuthConfiguration()`), because accepting
  external JWTs without signature verification permits forgery.
- **`.env` is never committed.** `.gitignore` excludes `.env` and `.env.*` (while keeping
  `.env.example`), plus `*.pem`, `*.p12`, `*.jks`, `*.keystore`. The `.env.example` file contains
  only placeholders.
- **Passwords.** Stored bcrypt-hashed (`User.passwordHash`) and never returned by any endpoint. The
  password hash field is explicitly documented as never exposed.
- **Webhook secrets.** Stored on `webhook_subscriptions` and deliberately excluded from
  `WebhookResponse`, so they are never returned by the API.
- **Logging.** Handlers are expected not to log credentials or tokens. Failed authentication logs a
  generic message (`"Could not set user authentication in security context"`) without the token.

## Authentication & authorization summary

- HTTP authentication is a bearer JWT (`HS512`, `sub` = userId). The `X-Tenant-Id` header is
  required for tenant-scoped routes, and the token's tenant claim must match it.
- Disabled/deleted accounts and password changes invalidate existing tokens immediately
  (user status + `pwdAt` stamp checked on every request).
- `SecurityConfig`: `/api/auth/ws-ticket` requires authentication; all other `/api/auth/**` routes
  and `/ws/**` are permitted at the filter level, with everything else authenticated. Domain
  authorization is centralized in `AuthorizationService`.
- WebSocket handshakes require a bearer token or a single-use ticket and re-validate tenant
  binding + membership; STOMP destinations are allowlisted and subscription/connection/payload
  limits are enforced.
- Webhook URLs are validated against SSRF at registration **and** at every delivery (loopback,
  private, link-local/metadata, CGNAT and reserved ranges rejected).

## Not implemented (security-relevant gaps)

- No REST rate limiting / brute-force protection on login.
- No refresh-token rotation or token revocation list.
- No CSP/security headers guidance is enforced at the application layer; TLS and headers must be
  terminated by a reverse proxy.
- `ALLOWED_ORIGINS` defaults to `*`.
- No dependency-vulnerability scanning in CI (there is no CI).

## Secret Manager Integration

For production deployments, secrets should not be distributed as plain environment variables.
Instead, integrate a secret manager and inject values at startup:

- **HashiCorp Vault.** Use the Vault Agent sidecar or the Spring Cloud Vault starter to read
  `JWT_SECRET`, `EXTERNAL_AUTH_JWT_SECRET`, database credentials, and webhook signing keys from a
  mounted secret path at startup. The application continues to read from `System.getenv()`, so the
  sidecar can write values to a `.env` file or inject them directly into the container environment
  before the JVM starts.
- **AWS Secrets Manager.** Store secrets as JSON secret values. A small init container or entrypoint
  script fetches the secret via the AWS CLI/SDK and exports the individual keys as environment
  variables before launching the application. Never embed the secret value in the Docker image.
- **Azure Key Vault / GCP Secret Manager.** Equivalent pattern: init container fetches the named
  secret versions and exports them as environment variables; the application reads them via Spring's
  `${ENV_VAR:default}` property resolution.

**Rotation.** Rotate `JWT_SECRET` and webhook signing keys on a schedule. After rotation, restart
the application so the new key material is loaded. Old JWTs signed with the previous secret will
fail verification, which is the intended behavior for compromised-key rotation.

**Do not.** Do not commit `.env`, `.env.production`, `secrets.json`, or any file containing key
material to version control. `.env.example` may contain placeholder names only.

