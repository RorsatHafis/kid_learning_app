# Deploying Bookeyond Kid (staging and production)

This is for whoever runs Bookeyond Kid on a server. To try it on your own Windows computer,
use [OWNER-GUIDE.md](OWNER-GUIDE.md) instead.

What has been verified, and how, is recorded in
[BOOKEYOND-KID-ENGINEERING-PROGRESS.md](BOOKEYOND-KID-ENGINEERING-PROGRESS.md). The whole procedure
below - settings check, release, smoke test, upgrade, rollback, backup, restore drill - has been
**rehearsed locally** on the production Compose file with the `prod` profile and a real TLS proxy
(section 5a). It has **not been run on a real server**: the server, the domain and its DNS, the mail
provider, off-server backup storage and the secrets are external and only you can supply them.
[LAUNCH-CHECKLIST.md](LAUNCH-CHECKLIST.md) lists them in order.

---

## 1. What runs

| Part | Image | Notes |
|---|---|---|
| Web | `frontend/Dockerfile` (nginx) | Serves the app, forwards `/api/` to the backend, adds the security headers (strict CSP, no framing, no referrer). The only part exposed to the internet. |
| Backend | `backend/Dockerfile` (Java 21) | Runs as a non-root user. Health: `/actuator/health/readiness` and `/actuator/health/liveness` (public, no details). Every other actuator endpoint is administrators-only. |
| Database | PostgreSQL 18 | Schema is created and upgraded by Flyway at backend start-up (`V1` … `V55`), before the backend reports healthy. |
| Mail | Any SMTP provider | Sign-up confirmation, password reset, account set-up and change notices. |

The root `docker-compose.yml` wires these for **local use** (Mailpit instead of real mail, the
`local` profile, ports bound to 127.0.0.1). **`deploy/compose.production.yml`** is the server
deployment: the same two images (tagged per release), the `prod` profile, PostgreSQL, separate volumes
for the database, media and the erasure ledger, and **Caddy** as the TLS proxy (Let's Encrypt
certificates, http redirected to https). Only Caddy publishes ports (80 and 443).

## 2. Topology

```
browser ──HTTPS──> TLS proxy / load balancer ──HTTP──> web (nginx :80) ──/api/──> backend (:8080) ──> PostgreSQL
```

- Terminate TLS in front of `web`. The browser only ever talks to one origin, so no CORS is
  involved for the app itself.
- **Client addresses** (sign-in, registration and recovery limits are per client address):
  behind a proxy, the address nginx sees is the proxy's, and every user would share one budget.
  `deploy/compose.production.yml` mounts **`deploy/nginx-real-ip.conf`**, which makes the web front
  believe the client address Caddy reports (only from the private container network). With a different
  proxy or load balancer, put its address range there instead.
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

On a Compose deployment the values live in **`deploy/production.settings`** - copy
`deploy/production.settings.example`, fill in every `CHANGE_ME`, keep it `chmod 600` and out of git
(it is ignored), and check it: **`scripts/check-production-settings.sh deploy/production.settings`**
reports anything missing, a placeholder left, a short or local secret, a public address that doesn't
match the domain, half-set legal settings, variables the backend would refuse, and whether the
Compose file resolves. `deploy/compose.production.yml` sets `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`,
`DB_USERNAME`, `MEDIA_ROOT` and `ERASURE_LEDGER_FILE` itself. The backend still **refuses to start**
when a required value is missing or unsafe (`ConfigurationContractTests`).

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
`PLATFORM_BOOTSTRAPADMIN_PASSWORD` (12+ characters, at most 72 bytes, and not easy to guess - not one
character repeated, counting, a keyboard row, "password" or the app's name, or the email itself; the
backend refuses to start with one that fails, saying why). It is only created when no administrator
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
only, so that restoring a database backup can't bring an erased child or account back — see section 6),
`LEARNING_DAY_ZONE` (Asia/Phnom_Penh: when a child's daily learning time starts again, M3),
`CHILD_DEVICES_MAX_PER_CHILD` (3: how many devices may hold one child's sign-in, M10a - owner decision D-05),
`CAREGIVERS_MAX_PER_FAMILY` (2: how many people outside the family may help with its children, M10b - D-05).

**Payments** (Y43, all off by default; see [docs/PAYMENTS.md](docs/PAYMENTS.md)):
`PAYMENTS_MANUAL_ENABLED` and `PAYMENTS_MANUAL_INSTRUCTIONS` (bank transfer, confirmed by an administrator
against the real receipt; only with the owner's real instructions), `PAYMENTS_PENDING_LIFETIME` (PT30M),
`ABA_PAYWAY_ENABLED`, `ABA_PAYWAY_BASE_URL`, `ABA_PAYWAY_MERCHANT_ID`, `ABA_PAYWAY_API_KEY`,
`ABA_PAYWAY_RETURN_URL`, `ABA_PAYWAY_SUCCESS_URL` (only after sandbox testing with ABA's credentials).
`PAYMENTS_DEV_SIMULATOR_ENABLED` must stay unset or `false`: production refuses to start with test payments on.

Keep secrets in your platform's secret store or password manager, never in the repository.
`backend/.env.example` is out of date (TD-02, a file the engineering agent may not edit): use this
table and `deploy/production.settings.example`, and bring `.env.example` in line or delete it.

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

**On the server, once:** a Linux host with Docker Engine and the Compose plugin; the repository cloned
to e.g. `/opt/bookeyond-kid`; DNS for the domain pointing at the server; ports 80 and 443 open (and
nothing else); `deploy/production.settings` filled in and passing the check; `gpg --import` of the
backup public key (section 6).

**Each release** - only a commit whose CI is green:

```
cd /opt/bookeyond-kid && git fetch && git checkout <commit>
scripts/release.sh deploy/production.settings
```

`scripts/release.sh` refuses a working tree with changes, then:
1. checks the settings (`scripts/check-production-settings.sh`);
2. if a release is running, **backs it up** (`scripts/backup.sh` into `BACKUP_DIR`, default
   `/var/backups/bookeyond-kid`) and notes which release it was (`previous-release` there);
3. builds both images tagged with the commit and starts them: PostgreSQL healthy -> backend (Flyway
   applies new migrations first; healthy only once the application is ready) -> web front (waits for a
   healthy backend) -> Caddy. It waits until every service is healthy (10 minutes at most);
4. runs **`scripts/smoke-test.sh`** on the public address: https with a trusted certificate, HSTS and
   the security headers, http redirected, the API answering through the proxy, the app's own origin
   accepted and another site refused, the actuator not reachable from outside. Read-only; safe on
   production at any time.

If step 3 or 4 fails it stops and prints the way back.

**The first release:** add the three first-administrator lines to the settings, release, sign in,
then delete those lines and run `scripts/release.sh` again (same commit: a backup and a restart).
Then set up the backups (section 6).

**Rollback.** Images are kept per release, so going back is starting the previous one:

```
RELEASE=<previous release> SETTINGS_FILE=$PWD/deploy/production.settings \
  docker compose -p bookeyond-kid-prod --env-file deploy/production.settings -f deploy/compose.production.yml up -d --wait
```

Migrations only move forward: if the release you are leaving applied a migration (its notes or the
backend log say so), restore the backup taken in step 2 **first** (`scripts/restore.sh`, section 6)
and then start the previous release. Without a migration, starting the previous release is enough.

**Health and restarts.** Every service has a health check and `restart: unless-stopped`: a crashed
container is restarted by Docker, and the whole stack comes back after a server reboot. The backend's
readiness covers the database; mail is excluded, so a mail outage never takes the app down. The web
front finds a restarted or replaced backend by itself (section 2). Watch with
`docker compose -p bookeyond-kid-prod ps` and `... logs -f backend`.

## 5a. A rehearsal on your own computer

The same procedure runs locally with `deploy/compose.rehearsal.yml` (a mail catcher instead of a
provider; Caddy's own certificate authority for `localhost`) under a separate project, so it shares
nothing with a real deployment. With a rehearsal settings file (`APP_DOMAIN=localhost`,
`APP_PUBLIC_URL=https://localhost:8443`, `HTTP_PORT=8080`, `HTTPS_PORT=8443`, `MAIL_HOST=mail`,
`MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_SMTP_STARTTLS=false`, random secrets):

```
PROJECT=bk-rehearsal BACKUP_DIR=<folder> EXTRA_COMPOSE=deploy/compose.rehearsal.yml REHEARSAL=1 REHEARSAL_CA=1 \
  HTTP_URL=http://localhost:8080 scripts/release.sh <rehearsal settings>
```

`REHEARSAL_CA=1` makes the smoke test trust the rehearsal's local certificate authority (TLS is still
checked); emails are at http://localhost:8045. This is how the procedure was verified (progress
record, Y31). Run E2E journeys against it with `E2E_IGNORE_HTTPS_ERRORS=1`.

## 6. Backups and recovery

**Daily backups** - `deploy/backup.cron.example` is the crontab to install (it uses the scripts below):

- **`scripts/backup.sh <folder>`** writes `bookeyond-kid-<time>/`: the database (`pg_dump` custom
  format, read back to make sure it is complete), the media files (taken after the database, so they
  hold every file it refers to), the erasure ledger, a `manifest.txt` (schema version, row counts,
  media files) and `SHA256SUMS`.
- **`scripts/encrypt-backup.sh <folder> <key>`** encrypts a copy to the backup **public** key (gpg) for
  storage **off the server** - another provider or region (the cron line uses `rclone`; use your own
  tool). The private key stays offline, with a second copy somewhere safe: without it, off-server
  backups can't be read. Key setup is in the script's header.
- **`scripts/prune-backups.sh <folder> --yes`** keeps the newest `KEEP_DAILY` (14) local backups and one
  for each of `KEEP_WEEKLY` (8) older weeks; without `--yes` it only lists. Off-server copies need
  their own retention rule in your storage (e.g. expire after the period you promise).
- **How long backups are kept is a promise to families** (PD-24): erased data lives on in backups until
  they expire. Decide it, state it in the privacy notice (PD-18), and set the numbers to match.
- A managed database's own backups (with point-in-time recovery) can replace the `pg_dump` part; media
  and the ledger still need copying.

**Restore drill** - monthly (in the cron file) and after any change to backups:
**`scripts/restore-drill.sh <backup folder or .tar.gpg>`** restores into a **throwaway** PostgreSQL
container (removed afterwards; nothing live is touched), compares schema version and row counts with
the manifest, and checks that every media file the database refers to is in the backup. Run it on the
off-server copies too, from the machine that holds the private key.

**Restoring** - **`scripts/restore.sh <backup folder> --yes`** checks the checksums first (a damaged
backup changes nothing), stops the backend, puts the database back, adds the backup's media and starts
the backend. Everything written since the backup is lost. It never restores the erasure ledger.
On the production stack (the release currently running in `RELEASE`):

```
RELEASE=<running release> SETTINGS_FILE=$PWD/deploy/production.settings COMPOSE="docker compose -p bookeyond-kid-prod --env-file deploy/production.settings -f deploy/compose.production.yml" BACKEND=bookeyond-kid-prod-backend-1 DB=bookeyond-kid-prod-db-1 scripts/restore.sh <backup folder> --yes
```

Then run `scripts/smoke-test.sh https://<domain>`.

- The audit log (`audit_events`) cannot be updated or deleted by the application (a database
  trigger refuses it). Keep it in backups.
- **Deletion and backups.** When a family deletes a child or their account, the data is erased
  from the live database after the grace period (Y3); it remains in backups taken before that
  until those backups expire. **After restoring an old backup, deletions carried out since then are
  carried out again by themselves (Y18):** every erasure is also written to `ERASURE_LEDGER_FILE`
  (when, child or account, the id - no names), and the sweep erases again anything listed there that a
  restore brought back, without emailing the family a second time (audited as `CHILD_ERASED` /
  `ACCOUNT_ERASED` with `afterRestore`). So that file lives **on its own volume**, is backed up
  separately, and is **never replaced by an older copy**. The application log also says
  `Erased CHILD <id>` for each one, as a second record.
- Media files are named by their content (SHA-256) and never changed once written, so a database
  restore needs media taken at the same time or later. Running more than one backend instance needs
  that directory on shared storage (or an object store - not built).

## 6a. When something goes wrong

- **A release won't start (failed migration).** The backend log names the migration; the backend never
  reports healthy and `release.sh` stops. By then the old containers have been replaced, so go back:
  restore the backup `release.sh` took just before (`scripts/restore.sh`), start the previous release
  (section 5), and fix the migration in a new commit. Never edit `flyway_schema_history` or run `flyway repair` on production
  without understanding why the migration failed.
- **The app is down.** `docker compose -p bookeyond-kid-prod ps`: a service that isn't healthy, then its
  logs. Docker restarts crashed containers. If PostgreSQL's volume is damaged, restore the newest backup.
  If the server is lost: a new server, the same settings file (from your secret store), DNS pointed at it,
  the newest off-server backup decrypted on a trusted machine and copied over, `scripts/release.sh`
  once (an empty stack), then `scripts/restore.sh` with that backup.
- **Erased data came back with a restore.** Restart the backend (`scripts/restore.sh` does): before it
  takes a request it erases again whatever the ledger lists (Y45; the log says `again at startup`), and the
  hourly sweep does the same. A database restored *without* restarting the backend stays as restored
  until that sweep. Check the log for `was erased but is back`. If the ledger itself was lost, the
  application log's `Erased CHILD/ACCOUNT <id>` lines (and the `CHILD_ERASED` / `ACCOUNT_ERASED` audit
  events in any newer backup) name what to erase.
- **Media files are missing** (a picture or recording fails to load; the log says `Media file for asset
  <id> is missing from storage`): copy them back from the newest backup's `media.tar` into the media
  volume - files are only ever added, so restoring media never overwrites anything newer. Uploading the
  same file again in the Studio also repairs it (Y45).
- **A secret is exposed.** `JWT_SECRET`: replace it and restart the backend - everyone is signed out.
  `DB_PASSWORD`: change it in PostgreSQL (`ALTER ROLE bookeyond_kid PASSWORD ...`) and the settings, restart
  the backend. Mail credentials: rotate at the provider. The backup private key: make a new key pair,
  re-encrypt what you keep, destroy the old copies.

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

## 8. Turning on CI

`.github/workflows/ci.yml` runs on every push and pull request once the repository has a GitHub remote
(it has not run yet - there is no remote): the backend suite on Testcontainers, frontend lint, types,
tests, build and dependency audit, the Docker stack's E2E journeys on desktop and phone, the legal and
review journeys with those features on, `scripts/verify-runtime.sh`, and a check of the production
deployment files and scripts. It needs **no secrets**: every value it uses is generated or a test value.
To turn it on: push the repository to GitHub, allow Actions (Settings -> Actions -> General), and
protect the release branch so merging requires the `CI` checks to pass.

## 9. Before real families use it

These are not engineering tasks and are not done:

- Privacy notice and terms (PD-18) — legal text you publish; set `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL` and
  `LEGAL_VERSION` and the app links them everywhere and records each account's agreement.
- Confirm the deletion rules adopted in Y3 (PD-24): what is erased, the waiting period, and how
  long backups keep erased data. Families can already export their data and delete a child or
  their account.
- The product decisions listed at the end of [OWNER-GUIDE.md](OWNER-GUIDE.md).
