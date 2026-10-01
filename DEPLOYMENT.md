# Deploying Bookeyond Kid (staging and production)

This is for whoever runs Bookeyond Kid on a server. To try it on your own Windows computer,
use [OWNER-GUIDE.md](OWNER-GUIDE.md) instead.

What has been verified, and how, is recorded in
[BOOKEYOND-KID-ENGINEERING-PROGRESS.md](BOOKEYOND-KID-ENGINEERING-PROGRESS.md). Nothing here has
been run on a real server yet: a host, a domain, TLS, a mail provider and the secrets are
**external dependencies** that only you can supply.

---

## 1. What runs

| Part | Image | Notes |
|---|---|---|
| Web | `frontend/Dockerfile` (nginx) | Serves the app, forwards `/api/` to the backend, adds the security headers (strict CSP, no framing, no referrer). The only part exposed to the internet. |
| Backend | `backend/Dockerfile` (Java 21) | Runs as a non-root user. Health: `/actuator/health/readiness` and `/actuator/health/liveness` (public, no details). Every other actuator endpoint is administrators-only. |
| Database | PostgreSQL 18 | Schema is created and upgraded by Flyway at backend start-up (`V1` … `V41`). |
| Mail | Any SMTP provider | Sign-up confirmation, password reset, account set-up and change notices. |

The root `docker-compose.yml` wires these for **local use** (Mailpit instead of real mail, the
`local` profile, ports bound to 127.0.0.1). A server deployment uses the same two images with
the `prod` (or `staging`) profile and the variables below.

## 2. Topology

```
browser ──HTTPS──> TLS proxy / load balancer ──HTTP──> web (nginx :80) ──/api/──> backend (:8080) ──> PostgreSQL
```

- Terminate TLS in front of `web`. The browser only ever talks to one origin, so no CORS is
  involved for the app itself.
- **Client addresses** (sign-in, registration and recovery limits are per client address):
  the bundled nginx sets `X-Forwarded-For` to the address *it* sees. If a TLS proxy or load
  balancer sits in front of it, that is the proxy's address, and every user would share one
  budget. Add to `frontend/nginx/default.conf`, with your proxy's address range:
  ```
  set_real_ip_from 10.0.0.0/8;          # your load balancer's range
  real_ip_header X-Forwarded-For;
  real_ip_recursive on;
  ```
  The backend itself only believes `X-Forwarded-For` entries added by private/loopback
  addresses (`server.forward-headers-strategy: native`). If the proxy in front of the backend
  has a public address, set `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` to a regex matching it.
- Do not expose the backend or the database to the internet.

## 3. Configuration

Set `SPRING_PROFILES_ACTIVE=prod` (or `staging`). The backend **refuses to start** when a
required value is missing or unsafe (`ConfigurationContractTests`).

**Required**

| Variable | Meaning |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL JDBC URL and credentials |
| `JWT_SECRET` | Signing key for sign-in tokens, **at least 32 bytes**, random, never reused between environments. Changing it signs everyone out. |
| `JWT_ISSUER` | A name for this environment, e.g. `bookeyond-kid-prod` |
| `CORS_ALLOWED_ORIGINS` | The exact public origin, e.g. `https://app.example.org` (no `*`) |
| `APP_PUBLIC_URL` | The same origin; links in emails point here |
| `MAIL_HOST`, `MAIL_FROM` | SMTP server and the sender address |
| `MEDIA_ROOT` | A directory the backend can write, on **persistent storage**: the pictures and recordings educators upload. The backend refuses to start if it can't write there. |

**Usually needed:** `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`
(true), `MAIL_SMTP_STARTTLS` (true).

**First administrator** (set once, for the first start, then remove):
`PLATFORM_BOOTSTRAPADMIN_ENABLED=true`, `PLATFORM_BOOTSTRAPADMIN_EMAIL`,
`PLATFORM_BOOTSTRAPADMIN_PASSWORD` (12+ characters). It is only created when no administrator
exists. Everyone else is provisioned from inside the app.

**Tunable, with safe defaults:** `JWT_ACCESS_TOKEN_TTL` (15m), `JWT_REFRESH_TOKEN_TTL` (30d),
`REFRESH_TOKEN_REUSE_GRACE` (30s), `LOGIN_MAX_ATTEMPTS` (5), `LOGIN_LOCKOUT_DURATION` (15m),
`RATE_LIMIT_LOGIN_PER_MINUTE` (20), `RATE_LIMIT_REGISTER_PER_HOUR` (20),
`RATE_LIMIT_REFRESH_PER_MINUTE` (60), `RATE_LIMIT_RECOVERY_PER_HOUR` (10),
`MASTERY_LEARNING_RATE` (0.2), `MASTERY_CONFIDENCE_SATURATION_COUNT` (10),
`MASTERY_OBJECTIVE_THRESHOLD` (0.8), `SERVER_PORT` (8080).

Keep secrets in your platform's secret store, never in the repository. (`backend/.env.example`
is out of date — TD-02 — use this table.)

## 4. Mail

Use a transactional mail provider with your own sending domain, and publish **SPF, DKIM and
DMARC** records for it, or messages to parents will land in spam. Email bodies are never
logged; delivery failures are logged with the recipient's domain only. A mail outage does not
take the API down (mail health is excluded from readiness).

## 5. Releasing

1. Back up the database (below).
2. Build both images from the same commit; CI (`.github/workflows/ci.yml`) must be green:
   backend suite on Testcontainers, frontend lint/types/tests/build/audit, and the E2E +
   accessibility journeys against a fresh Docker stack.
3. Start the new backend. Flyway applies any new migrations before the app accepts traffic;
   readiness turns UP afterwards. Then roll the web image.
4. Check `/actuator/health/readiness`, sign in, and open a child's page.

**Rollback.** Migrations only move forward. To roll back a release that included a migration,
restore the backup taken in step 1 and redeploy the previous images; without a migration,
redeploying the previous images is enough.

## 6. Backups and recovery

- Take automated daily PostgreSQL backups (e.g. `pg_dump -Fc`) plus point-in-time recovery
  if your provider offers it, kept encrypted and off the database host.
- **Test a restore** into a scratch database before relying on it.
- The audit log (`audit_events`) cannot be updated or deleted by the application (a database
  trigger refuses it). Keep it in backups.
- Back up `MEDIA_ROOT` with the database: questions refer to their pictures and recordings,
  which live there as files named by their content (SHA-256), never changed once written. A
  database restore needs the media taken at the same time or later. Running more than one
  backend instance needs that directory on shared storage (or an object store - not built).

## 7. Observing it

- Logs: in `staging`/`prod`, one JSON (ECS) object per line with a `correlationId` per
  request; every error response carries the same id, so a parent's report can be traced.
  Sign-in failures and lockouts are logged by account id only — never addresses or passwords.
- Health: `/actuator/health/liveness` and `/readiness` for your orchestrator.
- Audit: administrators read account, staff, content and security events in the app
  (Admin → Activity log).
- Not set up yet (your choice of platform): metrics collection and alerting. `staging`
  exposes `/actuator/metrics` to administrators only.

## 8. Before real families use it

These are not engineering tasks and are not done:

- Privacy notice and terms (PD-18) — legal text; no page is shown until it exists.
- Consent and data-retention/deletion rules (PD-24); family data export exists.
- The product decisions listed at the end of [OWNER-GUIDE.md](OWNER-GUIDE.md).
