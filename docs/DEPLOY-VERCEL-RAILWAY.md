# Deploying to Vercel (frontend) and Railway (backend + PostgreSQL)

Status (2026-10-20): **prepared, not deployed.** The configuration below is in the repository, but no Vercel or
Railway project exists yet: creating them needs the owner's accounts, and Railway is a paid service
(volumes and always-on services need a paid plan). Nothing here has been verified on Vercel or Railway.

The self-hosted path (Docker Compose behind Caddy) remains documented in [`DEPLOYMENT.md`](../DEPLOYMENT.md).

Sources read on 2026-10-20: Railway's Spring Boot guide, volumes reference and config-as-code reference
(config-as-code is deprecated, so the Railway settings below are dashboard settings); Vercel's Vite guide.

## Shape

```
family's browser ──► Vercel (static React app, security headers)
                       └── /api/* rewritten ──► Railway "backend" service (Spring Boot, Dockerfile)
                                                   ├── Railway PostgreSQL
                                                   └── Railway volume /data  (media + erasure ledger)
```

The browser only ever talks to the Vercel domain: the app calls `/api/v1` and media links are relative
(`/api/v1/media/...`), so Vercel proxies `/api/*` to the backend. One origin means the strict
Content-Security-Policy (`connect-src 'self'`) keeps working.

**One backend replica only.** Media files and the erasure ledger live on one volume, which belongs to one
service instance. Do not add replicas or regions until media moves to object storage and the ledger to a
shared store.

## 1. Railway

1. New project → **Deploy from GitHub repo** → `RorsatHafis/kid_learning_app`, branch `master`.
2. Service settings:
   - **Root directory:** `backend` (the Dockerfile there builds and runs the jar as a non-root user).
   - **Builder:** Dockerfile.
   - **Healthcheck path:** `/actuator/health/readiness` (timeout 300 s; Flyway migrations run on start).
   - **Replicas:** 1.
3. Add **PostgreSQL** to the project.
4. Add a **volume** to the backend service, mounted at `/data`. Volumes mount as root while the image runs as
   user `app`, so set `RAILWAY_RUN_UID=0` (Railway's documented setting), or the app can't write to it.
5. **Networking → Generate Domain**, target port `8080`. Note the domain, for example
   `bookeyond-backend-production.up.railway.app`.
6. Variables (Railway's `${{Postgres.…}}` references point at the database service):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `JWT_ISSUER` | a name for this environment, e.g. `bookeyond-kid-staging` |
| `JWT_SECRET` | a new random value of at least 32 bytes (e.g. `openssl rand -base64 48`); never reuse a local one |
| `CORS_ALLOWED_ORIGINS` | the Vercel domain, e.g. `https://bookeyond-kid.vercel.app` (the proxy forwards the browser's `Origin`) |
| `APP_PUBLIC_URL` | the same Vercel domain (used in emailed links) |
| `MEDIA_ROOT` | `/data/media` |
| `ERASURE_LEDGER_FILE` | `/data/erasures/erasures.log` (production refuses to start without it) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | the owner's SMTP provider. Without real mail, email confirmation and password reset can't work |
| `RAILWAY_RUN_UID` | `0` (see step 4) |
| `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`, `LEGAL_VERSION` | once the owner publishes the privacy notice and terms (PD-18) |
| `PAYMENTS_DEV_SIMULATOR_ENABLED` | leave unset or `false`: production **refuses to start** if it is `true` |
| `PAYMENTS_MANUAL_ENABLED` / `PAYMENTS_MANUAL_INSTRUCTIONS` | only when the owner has real payment instructions (their own account details); never invent them |
| `ABA_PAYWAY_*` | leave off until ABA has issued merchant credentials and the sandbox has been tested (see `PAYMENTS.md`) |
| `PLATFORM_BOOTSTRAPADMIN_ENABLED`, `…_EMAIL`, `…_PASSWORD` | set once to create the first administrator, then set `ENABLED=false` and delete the password variable |

Secrets go in Railway's variables only, never in the repository.

## 2. Vercel

1. In `frontend/vercel.json`, replace `https://bookeyond-backend.invalid` with the Railway domain from step 1.5
   and commit. (`.invalid` never resolves, so nothing is sent anywhere until this is done.)
2. New project → import `RorsatHafis/kid_learning_app`, branch `master`.
   - **Root directory:** `frontend`. Framework preset Vite; build `npm run build`; output `dist`
     (also set in `vercel.json`).
   - No build-time variables are needed: the app uses relative `/api/v1`.
3. Deploy. The SPA fallback and the security headers (CSP, HSTS, frame denial and the rest, matching
   `frontend/nginx/security-headers.conf`) come from `vercel.json`.

## 3. Staging checks before anyone real uses it

Run each against the deployed URLs and record the result. A failure in any of the first six blocks release.

1. `GET https://<railway>/actuator/health` returns `UP`; `GET https://<railway>/actuator/metrics` is refused
   (administrators only); `https://<vercel>/` serves the app and a deep link such as `/parent/account` reloads.
2. Startup log shows Flyway migrating to V62 and `prod` active; no simulator.
3. Register a parent, confirm the email (real delivery), sign in, refresh, sign out.
4. **Rate limits per client** (sign-in, registration, refresh and recovery budgets are keyed on the client's
   address). The backend believes only `X-Forwarded-For` entries added by proxies it trusts: private-network
   addresses by default (Railway's last hop), plus whatever `SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES` lists (a Java
   regex of the *web front's* public addresses, unset by default). Behaviour in each case is covered by
   `ForwardedClientAddressIntegrationTests` and `TrustedFrontProxyClientAddressIntegrationTests`. On staging:
   1. **What the backend sees.** Sign in wrongly once, then find the warning `Rate limit exceeded ... from <address>`
      after step ii, or read the Railway request log. If `<address>` is your own public address, nothing more is
      needed. If it is a Vercel address, every family would share one budget: either list Vercel's egress
      addresses in `SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES` (only if Vercel gives the project fixed ones), or have
      the browser call the Railway domain directly (CORS, absolute media URLs and `connect-src` in the CSP).
      Don't release until this is settled.
   2. **Two clients, two budgets.** From one network (a phone on mobile data) sign in wrongly
      `RATE_LIMIT_LOGIN_PER_MINUTE` + 1 times: the last answer is 429 with `Retry-After`. At once, from a second
      network (office Wi-Fi), one wrong sign-in answers 401, not 429.
   3. **A spoofed header doesn't help.** From the first network, while it is still limited, send
      `curl -s -o /dev/null -w '%{http_code}' -X POST https://<vercel>/api/v1/auth/login -H 'Content-Type: application/json'
      -H 'X-Forwarded-For: 198.51.100.7' -d '{"email":"x@example.com","password":"x"}'`: still 429.
5. **Uploads through the real route.** In the Studio, through the Vercel domain, upload:
   1. a 1.9 MB picture and a 4.9 MB recording: both are accepted and play back;
   2. a 2.5 MB picture: the server's own message ("a picture at most 2 MB");
   3. a 6 MB recording: refused. If the answer is our backend's 413, the message names the limit. If Vercel's proxy
      refuses it first (its request limit may be below 5 MB), the Studio says "This file is too large to send";
      then lower `spring.servlet.multipart.max-file-size` to what Vercel accepts, or upload straight to Railway.
   Record the largest size Vercel passed.
6. Restart the backend service: uploaded media and `/data/erasures/erasures.log` are still there.
7. Payments: with no price set, families see "can't be bought online yet"; with manual payments on, a transfer
   stays pending until an administrator confirms the real receipt; `/payments/{id}/simulate` is refused (404 in
   `prod`).
8. A child plays an activity; the parent sees the evidence; a teacher and principal see only their classes.
9. Khmer and English, desktop and phone, on the deployed site.

## Local rehearsal (2026-10-20, Y44)

The same shape was rehearsed on one machine, with nothing public and no cloud account:
- "Railway": the image built from `backend/Dockerfile`, the `prod` profile and the variables of the table above
  (secrets generated locally), PostgreSQL 18, a `/data` volume with the process running as uid 0 as
  `RAILWAY_RUN_UID=0` does;
- "Vercel": the production build served by Caddy, whose headers, `/api/*` rewrite and SPA fallback were generated
  from `frontend/vercel.json` itself.

Rehearsal-only differences: mail went to Mailpit, manual payments carried test instructions, the register budget
was raised for the test families, and the bootstrap administrator stayed enabled.

Passed:
- **App and headers**: the app is served, a deep link reloads, every `vercel.json` header is present, no server
  header, and hashed assets are cached as immutable.
- **Backend**: readiness is UP, metrics are refused anonymously, Flyway applied V1–V62 with none failed, and `prod` is
  active.
- **Startup refusals**: production refuses to start with `PAYMENTS_DEV_SIMULATOR_ENABLED=true`, and refuses to start
  without `ERASURE_LEDGER_FILE`, each with a message naming the variable.
- **Payments**: `/payments/{id}/simulate` is unavailable and only bank transfer is offered.
- **Uploads**: an administrator signs in through the rewrite and uploads a picture, which is served back byte for
  byte. A tampered signature is refused. A 6 MB upload gets 413.
- **Volume**: media and the ledger file are on the volume and survive both a restart and a replaced container.
- **Browser journeys** through the rehearsed origin: golden 9/9, school (paid access: refused unpaid, invoiced and
  confirmed by an administrator, then open) 2/2, plans 4/4.

Not covered by the rehearsal: Vercel's and Railway's own platforms, real SMTP, the client-address check in step 3.4
(Vercel's proxy can't be simulated locally) and Vercel's request-size limit for uploads (step 3.5).

Found: if a stored media file is lost, uploading the same file again returned the existing record without writing the
file back (TD-26). Fixed in Y45, below.

### Erasure drill and fixes (2026-10-21, Y45)

On the same rehearsal stack, with deletions due at once and a sweep every minute:
- A family made by the golden journey (one child, 8 child-linked learning rows) deleted the child and then the
  account through the web origin. Each erasure left the database and wrote a ledger line (`CHILD`, then `ACCOUNT`);
  the parent's sign-in then answered 401.
- The ledger file was byte-identical (same SHA-256) after a backend restart and after the container was replaced.
- **Restore drill**, as `scripts/restore.sh` does it (backend stopped, a backup taken *before* the erasures restored,
  backend started). It showed a defect: for about 70 seconds the erased parent could sign in (200) and the child's
  rows were back, until the first sweep erased them again.
  **Fixed**: the backend now erases again, before its server takes a request, anything the ledger lists that the
  database holds (`ErasureStartupCatchUp`). In the repeated drill the first answer after the restore was 401, and the
  log shows the re-erasure finished before the application reported started. A restore done without restarting the
  backend is still only caught by the next sweep (hourly), so **restart the backend after any restore**.
- **Media (TD-26), fixed**: re-uploading a file repairs a missing or damaged stored copy (checked by SHA-256 and
  size, written beside it and moved into place, read back). A repair that can't be written fails and saves nothing.
  A per-content database lock also closes two races found on the way: simultaneous uploads of the same platform
  picture failing on the unique index, and a deletion removing a file another owner's upload was about to use.

## 4. Costs and approvals

Railway bills usage on a paid plan; a volume and an always-on backend plus PostgreSQL need one. Vercel's free
plan is for personal, non-commercial use; a business serving families may need a paid plan. Check both
providers' current pricing and approve the spend before creating the projects.
