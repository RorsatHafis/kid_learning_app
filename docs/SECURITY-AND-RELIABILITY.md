# Security, privacy and reliability - threat model, failure modes, data integrity

Part of the product review (`PRODUCT-JOURNEY-REVIEW.md`). Written 2026-11-03 from evidence produced that day: runtime
probes against the isolated stack (scripts in the session scratchpad), the full backend integration suite run locally
(**539/539**; three proxy tests hit the local Postgres connection limit in the full run and pass alone), the backend
unit suite (203/203), the full frontend suite (467/467) and the end-to-end journeys. Levels as in `CLAUDE.md` §6:
**TESTED** = an automated test asserts it; **RUNTIME** = exercised against the running stack; **INSPECTED** = read in
code or configuration only. Nothing here is claimed beyond its level.

## 1. What is protected, and from whom

| Asset | Why it matters | Who must not reach it |
|---|---|---|
| A child's learning record (answers, evidence, mastery, standings) | Children's data; parents' trust | Other families, schools without the family's grant, teachers outside the class, the public |
| A family's account and children | Identity, payment state | Other accounts; anyone guessing emails or tokens |
| School data (classes, staff, rosters) | Institutional trust | Other schools, branches without the organization role |
| Authored content (drafts, answer keys) | Learning integrity; authors' work | Other authors; children before publication; learners (answer keys) |
| Platform operations (accounts, plans, payments) | Money, access | Everyone but administrators with the authenticator step-up |
| Service availability | Families learning | Oversized or flooding requests |

## 2. Controls, threat by threat

### Identity and sessions

| Threat | Control | Evidence |
|---|---|---|
| Password guessing | Per-network token budgets on sign-in, registration, recovery, refresh; account lockout after repeated failures | TESTED `AuthRateLimitIntegrationTests`, `TokenBucketLimiterTests`, `SignInSecurityEventsIntegrationTests` |
| Learning which emails have accounts (sign-in, recovery) | Same answer for unknown emails; a dummy password hash is checked so timing doesn't tell (medians 64 vs 60 ms) | RUNTIME (probe) + INSPECTED `LoginService` |
| Learning which emails have accounts (registration) | **Open: S-ENUM-1** - registration answers 409 "An account already exists" (rate limited). Owner decision: changes the sign-up flow | RUNTIME |
| Stolen refresh token | Rotation on every use; reuse of a rotated token revokes the whole session family; sign-out everywhere | TESTED `SessionReliabilityIntegrationTests`, `AccountSettingsIntegrationTests` |
| Access token outliving sign-out or suspension | Every request checks its session is live and the account active (PD-02) | TESTED (admin suspension journey, e2e) |
| Administrator account takeover | Authenticator (TOTP) step-up for sensitive actions; wrong codes count toward lockout; a used code can't be replayed | TESTED `SecondFactorIntegrationTests`, `StepUpAndReconciliationIntegrationTests`, `StepUpTokenTests` |
| Token theft through XSS | Strict CSP (`script-src 'self'`, no inline), access token in memory; the refresh token in `localStorage` is a documented choice mitigated by rotation + reuse detection | INSPECTED (`frontend/nginx/security-headers.conf`, `vercel.json`, `tokenStore.ts`) |
| Forged client address to dodge budgets | Only a trusted front proxy's forwarded address is believed | TESTED `TrustedFrontProxyClientAddressIntegrationTests` |

### Authorization and privacy

| Threat | Control | Evidence |
|---|---|---|
| Reading or changing another account's resources by id (IDOR) | Role gate + the relationship each resource needs (`IDENTITY-PERMISSIONS.md`) | TESTED **`AuthorizationSweepIntegrationTests`** - every endpoint in the API's own description, with a real family's, school's and teacher's ids, refuses a stranger (>= 50 reads, >= 90 writes; proven to fail when one guard is removed); `CrossFamilyAccessSweepIntegrationTests`. RUNTIME: 172 probes, 0 open |
| A parent opening staff or admin data | The id-less endpoints a parent may open are an explicit allowlist (own or public) | TESTED (same sweep, second test) |
| Teacher content reaching unrelated family children | Path content scope (N7): a family path draws only platform content | TESTED `ContentTenancyIntegrationTests`; admin insights apply the same rule since 2026-11-03 (`ReadinessToBankIntegrationTests`, `CoverageGapScopeIntegrationTests`) |
| A school seeing a child without the family's grant | Joining a class needs the family's code; every school read requires the ACTIVE grant | TESTED `SchoolAccessIntegrationTests` |
| Answer keys reaching learners | Learner DTOs carry no correct answers; draft content never served | TESTED (Battle 3 suites) |
| A child's data kept after the family leaves | Export and erasure with a grace period, erasure ledger | TESTED `FamilyDataExportIntegrationTests`, `FamilyErasureIntegrationTests` |
| Secrets in responses or logs | No DTO carries hashes, tokens, secrets or codes; logs carry account ids only; the stand-in SMS sender refuses to start in production | INSPECTED (search of DTOs and log statements; `SmsSafetyGuard`) |

### Input, resources and the web surface

| Threat | Control | Evidence |
|---|---|---|
| Invalid or hostile input | Bean validation on bodies; domain checks where a body isn't annotated (fixed minute sets, ownership of class/year); LIKE searches escape `%`, `_`, `\` | INSPECTED (every `@RequestBody` without `@Valid` traced to a domain check) |
| Memory exhaustion by one large request | Non-multipart bodies capped at 1 MB in the backend (413 before reading; chunked bodies stop at the limit) - the hosted deployment has no nginx; uploads keep 5/6 MB multipart limits | TESTED `RequestSizeLimitIntegrationTests` |
| Malicious uploads | Type and content inspection, per-owner storage allowance | TESTED `MediaLibraryIntegrationTests`, `QuestionMediaAndOrderingIntegrationTests` |
| Signed media URLs forged | HMAC with a key derived from the token secret, constant-time comparison, hourly expiry | INSPECTED `MediaService` |
| Clickjacking, sniffing, referrer leaks | `frame-ancestors 'none'`, X-Frame-Options DENY, nosniff, `no-referrer`, HSTS behind TLS, COOP | INSPECTED |
| Browser features beyond need | Permissions-Policy denies camera, geolocation, payment, USB; **microphone `(self)`** for the Studio's recorder - it was `()` until 2026-11-03, which blocked recording in every production deployment | RUNTIME (Chromium with a fake microphone: old policy refused, new records) |
| CORS abuse | Exact origins only, refused at startup otherwise | TESTED `CorsPolicyIntegrationTests`, `ConfigurationContractTests` |
| Payment callback forgery | A provider's notice only makes the server ask the provider | TESTED `PaymentIntegrationTests` |

## 3. Failure modes

| Situation | What happens | Evidence |
|---|---|---|
| A response is lost and the user taps again (double submit) | Registration, answers, question pastes (bank and into an activity) and adding a child are idempotent by key - the same request again is refused or answers with what was already made | TESTED `QuestionImportOnceIntegrationTests`, `AddChildOnceIntegrationTests` (two simultaneous taps -> one child), `IdempotencyPersistenceIntegrationTests` |
| Two requests race | Unique constraints on every relationship (enrollment, class membership, staff, grants, review items, caregivers); path and attempt row locks | RUNTIME: 5 simultaneous enrollments -> one; 5 simultaneous joins -> idempotent; no 500. TESTED (Battle 5A/5D concurrency suites) |
| A step fails half-way | One transaction per operation (a paste: all or none; an activity with its questions: all or none); an idempotency guard released on failure so a retry is allowed | TESTED (import suites) |
| A token refresh's response never arrives | The client keeps a valid token; reuse detection doesn't punish an honest retry (N19) | TESTED `SessionReliabilityIntegrationTests` |
| Content is revised under a child | A path keeps the version it pinned (retired versions still start through the path, Battle 3R); playing a finished book again uses the current published version; missed questions still read the version played | TESTED `PlayAgainVersionIntegrationTests`; RUNTIME (retired version read for its child: 200) |
| A child's history grows | Reads cost a fixed number of statements | TESTED `ReadQueryScalingIntegrationTests`, `SchoolReadScalingIntegrationTests` (caught an N+1 on 2026-11-03, fixed in `c910f3a`) |
| The server is slow or down | Pages show their loading, error and retry states | TESTED (e2e `resilience.spec`) |

Performance measured on the dev data (160 curricula, 50+ classes): every endpoint timed answered under 50 ms (the
heaviest, learning operations, 46 ms); a parent's first load is about 170 KB gzipped (workspaces load on demand).

## 4. Open items (to the roadmap)

| # | Item | Severity | Owner / next |
|---|---|---|---|
| S-ENUM-1 | Registration reveals existing accounts | Medium | **Owner decision** (sign-up flow) |
| TD-15/OD-01 | Rate limits are per instance | Medium once scaled out | A shared store when there is more than one backend instance |
| SR-RT | Refresh token readable by script (localStorage) | Low with the CSP | Revisit only with a cookie-based session design (architecture decision) |
| SR-DOCS | Staging serves API docs (production doesn't) | Low | Acceptable for staging; keep production off |
| SR-KEY | A commit-time failure leaves an idempotency key "in progress" until it expires (a day) | Low | Same as answers; accepted |
