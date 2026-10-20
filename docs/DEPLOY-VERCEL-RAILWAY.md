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
4. **Rate limits per client.** Behind Vercel and Railway the backend must see each person's own address
   (`server.forward-headers-strategy: native` believes only private-network proxies). Sign in wrongly from two
   different networks and check that one network's budget doesn't block the other. If every request appears to
   come from one Vercel address, sign-in budgets would be shared by all families: fix before release (for
   example, call the Railway API directly with CORS and absolute media URLs, or trust a header only Vercel can
   set).
5. Upload a 5 MB image in the Studio through the Vercel domain (the proxy's request size limit is unverified).
6. Restart the backend service: uploaded media and `/data/erasures/erasures.log` are still there.
7. Payments: with no price set, families see "can't be bought online yet"; with manual payments on, a transfer
   stays pending until an administrator confirms the real receipt; `/payments/{id}/simulate` answers 400.
8. A child plays an activity; the parent sees the evidence; a teacher and principal see only their classes.
9. Khmer and English, desktop and phone, on the deployed site.

## 4. Costs and approvals

Railway bills usage on a paid plan; a volume and an always-on backend plus PostgreSQL need one. Vercel's free
plan is for personal, non-commercial use; a business serving families may need a paid plan. Check both
providers' current pricing and approve the spend before creating the projects.
