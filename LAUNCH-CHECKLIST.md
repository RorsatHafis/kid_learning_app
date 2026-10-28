# Launch checklist

(Reviewed 2026-10-28 against the improvement program; the business reference is
[docs/BUSINESS-REQUIREMENTS.md](docs/BUSINESS-REQUIREMENTS.md).)

What is left before real families use Bookeyond Kid. The software side is built and verified locally
(the whole release procedure was rehearsed on the production Compose file with a real TLS proxy -
[DEPLOYMENT.md](DEPLOYMENT.md) section 5a; the Vercel + Railway shape was rehearsed locally on 2026-10-20 -
[docs/DEPLOY-VERCEL-RAILWAY.md](docs/DEPLOY-VERCEL-RAILWAY.md)). Everything below needs you: an account, a payment, a
domain, a legal text or a decision. Nothing here has been done yet.

## A. External actions, in order

1. **Repository and CI.** (2026-10-20: `master` is pushed to `github.com/RorsatHafis/kid_learning_app`, a
   **public** repository whose `main` holds an unrelated earlier project, YulMe; decide whether to make
   `master` the default branch and whether the repository should be private.) Allow Actions
   (Settings -> Actions -> General) and protect the release branch so merging needs the `CI` checks.
   No secrets are needed. Wait for the first run to be green (DEPLOYMENT.md section 8).
   Before pushing, bring `backend/.env.example` in line with DEPLOYMENT.md section 3 or delete it: it is
   out of date (TD-02), and the engineering agent may not edit `.env` files.
2. **A server.** A Linux virtual machine (4 GB memory or more, 2 CPUs, 40 GB disk to start), with
   Docker Engine and the Compose plugin, SSH by key only, and a firewall allowing 22, 80 and 443 only.
   Clone the repository to `/opt/bookeyond-kid`.
3. **A domain.** Point an A (and AAAA, if the server has IPv6) record for the app's domain at the
   server. Caddy obtains the certificate itself on the first start; that needs the DNS in place and
   ports 80/443 open.
4. **A mail provider.** A transactional email service. Verify your sending domain there and publish
   the **SPF, DKIM and DMARC** records it gives you; note the SMTP host, port, user and password.
   Without these records, parents' confirmation and reset emails land in spam.
5. **Off-server backup storage and the backup key.** Storage with another provider or region (an
   object-storage bucket is enough), with an expiry rule matching your retention decision (B2). On a
   trusted computer, make the backup key pair (`scripts/encrypt-backup.sh` header), keep the private key
   offline with a second copy somewhere safe, and `gpg --import` the public key on the server.
6. **The settings.** On the server: `cp deploy/production.settings.example deploy/production.settings`,
   `chmod 600`, fill in every `CHANGE_ME` (secrets: `openssl rand -base64 48`), and run
   `scripts/check-production-settings.sh deploy/production.settings` until it says ready. Keep a copy in
   your password manager.
7. **The first release.** Add the first-administrator lines, `scripts/release.sh deploy/production.settings`,
   sign in at the domain, delete those lines, run `scripts/release.sh` again (DEPLOYMENT.md section 5).
8. **Backups on.** Install `deploy/backup.cron.example` (crontab, with your storage tool and key), run a
   backup once by hand, then `scripts/restore-drill.sh` on it - and once on an off-server copy from the
   computer holding the private key.
9. **Check it as a parent would.** Sign up with your own email: the confirmation arrives (not in spam);
   reset a password; add a child; play. From your own computer, `scripts/smoke-test.sh https://<domain>`.
10. **Watching.** An external uptime check on `https://<domain>`, alerts to your phone, and somewhere
    the logs are kept (DEPLOYMENT.md section 7). Not built in - your choice of service.
11. **Legal texts live** (B1): finish the drafts in `docs/legal/` (owner facts marked `[OWNER]`, qualified review of
    the questions marked `[REVIEW]`), publish them, set `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`, `LEGAL_VERSION`, run
    `scripts/release.sh`. From then on sign-up requires agreeing to them.
11a. **Administrators' authenticator app** (M9a): each administrator turns it on in their account before confirming
    payments or changing access (step-up asks for it). Keep the recovery codes offline.
11b. **Payment instructions** (D-07, M8b): enter the real bank-transfer / KHQR details in the app before invoicing a
    school; until then the method shows "not configured" and nothing is invented.
12. **Content** (B3): educators author and publish the curricula in the Content Studio.
13. Then invite the first families.

## B. Decisions

### Required before accepting real families

| # | Decision | What is ready for it |
|---|---|---|
| B1 | **Privacy notice and terms of use** (PD-18): the texts, written for your jurisdiction and for children's data, naming who processes it (hosting, email provider). | Three settings link them on every page, sign-up requires agreeing, every account's agreement and version is recorded, and a new version asks everyone again. |
| B2 | **Deletion and retention** (PD-24, adopted - please confirm): the 7-day waiting period, what is erased, **how long backups keep erased data**, and **how long expired sign-in records (IP, browser) are kept** - today until the account is erased (`docs/legal/DATA-INVENTORY.md` q.4). The same numbers must be in the privacy notice. | Deletion with password confirmation and waiting period, erasure that survives restores (ledger), backup pruning (`KEEP_DAILY`/`KEEP_WEEKLY`), `ERASURE_GRACE_PERIOD`. |
| B3 | **The curriculum** (PD-15): educator-authored, approved content - the app ships only a sample Maths path. | The Content Studio, versioning, publish checks, take-down, optional second-person review (B7). |
| B4 | **Language** (PD-14): a native-speaker review of the Khmer interface before families use it, and whether launch needs Khmer lessons. | The whole interface, errors and emails are bilingual (Y32), but the Khmer is Claude-drafted - review list in `KHMER-REVIEW-Y33.md`. Content can be in any language. |
| B5 | Confirm the behaviours adopted while building (PD-02 signing out at once, PD-25 archive, PD-26 archiving a school, PD-28 stopping a path). | All built and tested; each can be changed if you decide otherwise. |

### Required before charging money

| # | Decision | What is ready for it |
|---|---|---|
| C1 | **Prices, plans and a payment provider** (PD-09), refunds and cancellation terms (part of B1), receipts/invoices required in Cambodia, and how long anonymised payment records are kept after erasure. | Y43: a payment lifecycle that grants a plan only on a confirmed payment, bank transfer confirmed by an administrator against the receipt, school invoices, refunds that take back exactly what was paid, an append-only ledger. Prices are set per plan; none are set. ABA PayWay is prepared but untested: it needs ABA's sandbox credentials and a sandbox run ([docs/PAYMENTS.md](docs/PAYMENTS.md)). |
| C2 | Whether a path a child's **class** assigns counts against the family's plan (part of PD-09). | Counted today; no effect while the default plan is unlimited. |
| C3 | **Schools pay before using school services (Y44).** Set a price on each school plan, then invoice each school and confirm its payment against the receipt. Upgrading an environment that already has schools pauses them (principals see the payment panel, teachers a notice) until they are paid: administrator-granted school plans no longer count. | Invoices, confirmation by an administrator, renewal by the principal, and the paused state are built and tested ([docs/PAYMENTS.md](docs/PAYMENTS.md)). Families' own learning is never paused. |

### Required for legal compliance

| # | Decision | What is ready for it |
|---|---|---|
| D1 | Legal advice for the countries you serve on children's data (parental consent, schools' access, data export and deletion rights) - B1 and B2 depend on it. | Parents consent for each school, can end it, export a child's data and delete a child or account. |
| D3 | Cambodia's personal-data law status (PD-31, draft of July 2025) and a child-safety policy with a monitored contact (PD-32). | Minimal data, export, erasure with a ledger, no child-to-child contact or ads. |
| D2 | Data-processing agreements with the hosting and email providers. | Email bodies are never logged; logs name accounts by id only. |

### Later product improvements

| # | Decision |
|---|---|
| E1 | PD-20: whether platform content needs a second administrator's approval (built, off: `CONTENT_REVIEW_REQUIRED`). |
| E2 | PD-27: more practice when a child has played every published step. |
| E3 | PD-04: whether evidence gathered from a version later taken down still counts. |
| E4 | PD-30: handing a school to a new principal. (PD-29, a second parent or carer, is built: M10b caregivers.) |
| E5 | PD-13 the Learning Buddy's name, PD-16 one retry before the answer is shown, PD-23 landing-page wording. |
| E6 | Moving to Tailwind 4, to clear two build-time-only dependency advisories (DEPLOYMENT.md section 7a). |
| E7 | D-04: an SMS provider, if phone verification and reset-by-text should work in production (built, test sender only). D-06/D-12 (M11): object storage for media instead of the server disk. |
| E8 | D-18 (bring a skill back on another day to confirm "secure"), D-19 (who authors the real curriculum). |

Full records: the decision list at the end of
[BOOKEYOND-KID-ENGINEERING-PROGRESS.md](BOOKEYOND-KID-ENGINEERING-PROGRESS.md) and the table in
[OWNER-GUIDE.md](OWNER-GUIDE.md).
