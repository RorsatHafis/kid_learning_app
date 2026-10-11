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
  budget. Add to `frontend/nginx/default.conf.template`, with your proxy's address range:
  ```
  set_real_ip_from 10.0.0.0/8;          # your load balancer's range
  real_ip_header X-Forwarded-For;
  real_ip_recursive on;
  ```
  The backend itself only believes `X-Forwarded-For` entries added by private/loopback
  addresses (`server.forward-headers-strategy: native`). If the proxy in front of the backend
  has a public address, set `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` to a regex matching it.
- **The app's own address, as the backend sees it:** the bundled nginx passes on the address the
  browser used - host and port (`Host`, `X-Forwarded-Port`) and scheme (a TLS proxy's
  `X-Forwarded-Proto` when there is one, else its own) - so requests from the app's own pages are
  recognised as the same origin however it is reached (another port, a LAN address for testing on a
  phone, https at a proxy). `CORS_ALLOWED_ORIGINS` is then only about other origins. A TLS proxy in front
  must set `X-Forwarded-Proto` (most do by default).
- **Where the web front finds the backend:** `BACKEND_ORIGIN` on the web container (default
  `http://backend:8080`, the Compose service). nginx looks the name up through the container's own DNS
  as requests arrive (an answer is kept 2 s), so a backend replaced on a new address - a redeploy or a restart -
  is reached again without restarting the web container; while the backend is down, `/api` answers 502.
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
`MASTERY_OBJECTIVE_THRESHOLD` (0.8), `SERVER_PORT` (8080),
`LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`, `LEGAL_VERSION` (unset: set all three once the privacy notice and
terms of use are published - PD-18; sign-up then requires agreeing to that version, and changing `LEGAL_VERSION` asks
every account to agree again), `CONTENT_REVIEW_REQUIRED` (false: when true, platform content is published only by a second administrator
approving a review of the draft - PD-20; add a second administrator first, Admin → Staff), `SESSION_CHECK_ENABLED` (true: every request checks its sign-in is still live, so signing out, a password
change or a suspension takes effect at once - PD-02), `SESSION_CHECK_CACHE_TTL` (10s: how long one answer is
reused, 0s-1m), `ERASURE_GRACE_PERIOD` (7d: how long a family's deletion request waits before it is carried
out, so a mistake can be undone; 0s–90d), `ERASURE_SWEEP_INTERVAL` (PT1H: how often due
deletions run), `ERASURE_SWEEP_ENABLED` (true; with several instances every instance may run
it — each deletion locks its rows), `ERASURE_LEDGER_FILE` (**required in `prod`**; e.g.
`/var/lib/bookeyond/erasures/ledger.log` on its own volume: the file where every erasure is recorded, ids
only, so that restoring a database backup can't bring an erased child or account back — see section 6).

Keep secrets in your platform's secret store, never in the repository. (`backend/.env.example`
is out of date — TD-02 — use this table.)

## 4. Mail

Use a transactional mail provider with your own sending domain, and publish **SPF, DKIM and
DMARC** records for it, or messages to parents will land in spam. Email bodies are never
logged; delivery failures are logged with the recipient's domain only. A mail outage does not
take the API down (mail health is excluded from readiness).

## 4a. Checking a running stack

`scripts/verify-runtime.sh` checks a running Compose stack through its web front: security headers,
migrations, refusals (bad credentials, malformed and oversized requests, other sites), a picture
uploaded and served back, file ownership, a backend restart and a backend replaced on a new address
(the web front must reach it without being restarted), and that media survives both. CI runs it on
every build. Run it against a test stack only - it uploads a picture and restarts the backend:

```
WEB=http://localhost:5173 ADMIN_EMAIL=... ADMIN_PASSWORD=... scripts/verify-runtime.sh
```

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
- **On a Compose deployment, `scripts/backup.sh <folder>`** writes one dated folder: the database
  (`pg_dump` custom format, checked readable), the media files and the erasure ledger, with checksums;
  run it daily (cron) and copy the folder off the host, encrypted. **`scripts/restore.sh <that folder>
  --yes`** checks the checksums first (a damaged backup changes nothing), stops the backend, puts the
  database back, adds the backup's media and starts the backend; it never restores the ledger over the
  live one. Both were run end to end on a test stack (Y25): what was written after the backup was gone,
  what was in it was back, media still served, ownership kept. A managed database's own backups can
  replace the `pg_dump` part.
- **Test a restore** into a scratch database before relying on it.
- The audit log (`audit_events`) cannot be updated or deleted by the application (a database
  trigger refuses it). Keep it in backups.
- **Deletion and backups.** When a family deletes a child or their account, the data is erased
  from the live database after the grace period (Y3); it remains in backups taken before that
  until those backups expire. Choose a backup retention period and state it in the privacy
  notice (PD-18). **After restoring an old backup, deletions carried out since then are carried
  out again by themselves (Y18):** every erasure is also written to `ERASURE_LEDGER_FILE` (when,
  child or account, the id — no names), and the sweep erases again anything listed there that a
  restore brought back, without emailing the family a second time (audited as `CHILD_ERASED` /
  `ACCOUNT_ERASED` with `afterRestore`). So keep that file **out of the database's backups and
  restores**: on its own volume, backed up separately, and never replaced by an older copy. The
  application log also says `Erased CHILD <id>` for each one, as a second record.
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

## 7a. Dependency advisories

`npm audit --omit=dev` (what the browser receives) reports **no vulnerabilities**, and CI fails if a high one
appears. Two advisories remain in **build-time tools only** - nothing from them is shipped, and they only ever
read this repository's own CSS and file patterns, never anything a user sends:

| Package | Advisory | Reached through | Why not fixed |
|---|---|---|---|
| `braces` 3.0.3 | GHSA-vfj7-8cjw-p6xm (high): deeply nested brace patterns exhaust the stack | Tailwind 3 (file watching and content globs), the TypeScript ESLint 7 parser | No patched `braces` exists (every version is affected) |
| `postcss-selector-parser` 6.1.4 | quadratic parsing of flat selectors (moderate) | Tailwind 3 | Fixed only in 7.1.6; Tailwind 3 requires 6.x |

**Action:** move to Tailwind 4 (a styling migration - plan it as its own piece of work) or to a Tailwind 3
release that drops these dependencies, when one exists; re-check with `npm audit` after each dependency update.

## 8. Before real families use it

These are not engineering tasks and are not done:

- Privacy notice and terms (PD-18) — legal text you publish; set `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL` and
  `LEGAL_VERSION` and the app links them everywhere and records each account's agreement.
- Confirm the deletion rules adopted in Y3 (PD-24): what is erased, the waiting period, and how
  long backups keep erased data. Families can already export their data and delete a child or
  their account.
- The product decisions listed at the end of [OWNER-GUIDE.md](OWNER-GUIDE.md).
