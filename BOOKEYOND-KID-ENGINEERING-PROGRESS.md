# Bookeyond Kid — Engineering Progress (canonical)

**The single source of truth for engineering progress across sessions.**
`CLAUDE.md` is the engineering constitution and governs; this document records where the
project actually stands against it. Where the two disagree on a verification level, the
evidence recorded here is newer (see §26 note under Battle 4A) — `CLAUDE.md` is not edited
from here.

- **Last reconciled:** 2026-09-26, after W2.0 (school ↔ family boundary closed) and W4.1 (rate limiting, session resilience, frontend test harness).
- **Reconciled against:** current source, Flyway migrations V1–V36, the last full test run
  (`./mvnw -Pintegration test`: **210 run, 209 pass, 1 known stale failure** — TD-04; frontend `npm test`: **13 pass**), and the
  runtime/browser evidence gathered in each battle.
- **Version control:** git repository at `D:\Bookeyond-Kid` (baseline commit `4db89ad`,
  then one commit per battle). TD-01 resolved.

### How to maintain this document

1. Update it at the end of every battle, in the same session.
2. Record only evidence that was actually produced; use the `CLAUDE.md` §6 levels exactly.
   A level observed in an earlier battle and not re-run is labelled "at battle time".
3. Move items between statuses only with evidence. Never mark an area complete because
   code exists or compiles.
4. Keep finding IDs stable (L*, N*, 3R.*, TD-*, PD-*); never reuse an ID.

### Status legend

| Code | Meaning |
|---|---|
| **VC** | VERIFIED COMPLETE — behavior correct for its accepted scope, with automated tests and runtime evidence |
| **PV** | IMPLEMENTED BUT PARTIALLY VERIFIED — works as far as verified; a required level of evidence is missing |
| **OD** | OPEN / KNOWN DEFECT — confirmed wrong or missing behavior |
| **PD** | PRODUCT DECISION REQUIRED — correct behavior is not established; must not be invented |
| **FP** | FUTURE PHASE — deliberately not built yet |
| **TD** | VERIFIED TECHNICAL DEBT — confirmed, not currently a user-facing defect |

Verification levels (`CLAUDE.md` §6): INSPECTED · COMPILED · TESTED · RUNTIME VERIFIED · BROWSER VERIFIED · NOT VERIFIED.

---

## Headline

**Working mode changed (2026-09-25):** the master production build directive replaces
battle-by-battle findings with product waves (W1 child + parent, W2 teacher + school, W3
admin + Content Studio, W4 identity + security, W5 subscription, W6 release). Finding IDs
stay stable; wave slices are recorded in the register below like battles.

**BACKEND FOUNDATION: integrity baseline established — not production-complete.**
The core learning loop (content → path → attempt → evidence → mastery → adaptive/review →
parent insight) is server-authoritative end to end since 5D, and authentication/session
state, configuration and content versioning have each been hardened and verified to the
levels below. The content tenancy boundary (N7) is enforced since W1.1. Open items
remain in learning-loop policy (PD-11, PD-12, OD-10) and security hardening (rate limiting).

**FULL BOOKEYOND KID PRODUCT: NOT COMPLETE.**
W1.1 delivered the first real child and parent learning experience (journey, play, feedback,
Learning Buddy, evidence-grounded parent overview) — BROWSER VERIFIED on desktop and mobile.
Still unbuilt or partial: canonical content beyond one Maths curriculum (educator-authored,
PD-15), Khmer UI (PD-14), parent account/settings, goals, school domain authorization,
principal/admin consoles and Content Studio, identity verification, subscription and
deployment. Do not describe the product as complete.

---

## Accepted battle register

| Battle | Scope | What was established | Highest verification | Evidence | Residual |
|---|---|---|---|---|---|
| **2A** | Learning-path / attempt integrity | An attempt's path item must belong to the child and pin the same ActivityVersion ("Known Backend Fix #2/#2b") | TESTED (unit); RUNTIME VERIFIED (3R: another child's path item → 403) | `ActivityAttemptService.requireLearningPathItemBelongsToChild`; `ActivityAttemptServiceTests` (3 start cases) | Verification level at original acceptance not recorded in the repo |
| **2B** | Auto-enrollment | Best-effort enrollment when exactly one ACTIVE curriculum matches the child's age hub; never a fabricated default | INSPECTED only | `ChildService.completeAutoEnrollment`, `EnrollmentService.autoEnrollIfUnambiguous` | **No automated test** (no test references `autoEnroll`) → PV |
| **2C** | Evidence-strength Parent Insight | Claims require minimum evidence; weak evidence is not shown as mastery | TESTED | `ParentInsightService` (`MIN_EVIDENCE_*`); `ObjectiveInsightTruthfulnessTests` (12), `ParentInsightTruthfulnessIntegrationTests` (4) | Extended by 5C |
| **2D** | Adaptive / Smart Review transactional reliability | Answer persistence, evidence, mastery and the idempotency guard commit in one transaction | TESTED (idempotency, persistence; end-to-end loop via the 5D golden journey) | `ActivityAttemptService`/`AnswerSubmissionWriter` ("Fix B + Battle 2D"); `IdempotencyServiceTests` (11), `IdempotencyPersistenceIntegrationTests` (3) | — |
| **3** | Content read-back / localization / audio | Learner-safe DTOs (no answer keys); DRAFT never served; an ActivityVersion cannot publish over unpublished questions | TESTED (3R lifecycle tests re-cover the read path) | `ActivityContentController`, `ActivityService.publish`, `ContentLifecycleIntegrationTests` | Localization/audio portion **not re-inspected** in this reconciliation |
| **4A** | Refresh-token security | Family revocation survives the reuse/race exception (`noRollbackFor`) | TESTED + RUNTIME VERIFIED (via 4D) | `SessionReliabilityIntegrationTests` replay/concurrency cases | `CLAUDE.md` §26 still says COMPILED; the 4D evidence supersedes it |
| **4B** | System-owned content authorization | Null-owner (system) content writable by ADMIN only | COMPILED; RUNTIME (positive path only: ADMIN republished system questions in 3R) | `ContentAccessGuard.requireOwnerWrite` | **No test that a TEACHER is refused** system content → PV |
| **4.1** | Build integrity | `assignCurriculum` response type fixed | COMPILED (whole suite compiles and runs) | — | — |
| **5A** | Answer, attempt & path integrity | Canonical answer contract (MC = option id; canonical TRUE_FALSE/NUMERIC; unscorable → 422); MATCHING refused at publish; completion requires every question; path-item insertion serialized by a row lock | TESTED; RUNTIME VERIFIED at battle time | `AnswerScoringService`, `LearningPathRepository.findByIdForUpdate`, `SmartReviewService`/`AdaptiveEngine` lock; `AnswerScoringServiceTests` (7), `ActivityAttemptServiceTests` | Runtime not re-run since |
| **5B** | Learning-state trust (cold start) | Bias-corrected EWMA mastery (V34); REINFORCEMENT needs ≥ 2 evidence | TESTED; RUNTIME VERIFIED at battle time | `MasteryEngine`, V34; `LearningStateColdStartTests` (12), `ColdStartLearningStateIntegrationTests` (3) | — |
| **5C** | Parent Insight truthfulness | Objective rollup over *observed* skills only (V35); coverage and evidence strength kept separate; the insight list is the CURRENT interpretation, snapshots are history | TESTED; RUNTIME VERIFIED at battle time | `MasteryEngine`, `LearnerObjectiveState`, `ParentInsightService`/`Controller`, V35 | Parent UI doesn't show coverage/history (PD-06) |
| **4D** | Session reliability | 401 (not 403) for unauthenticated; RFC 6750 challenge; logging out a rotated token revokes the family; replay detection without a chain-length cap; frontend single-flight + cross-tab Web Lock refresh | TESTED; RUNTIME; **BROWSER VERIFIED** | `ProblemDetailAuthenticationEntryPoint`, `AccountSessionService`, `client.ts`, `AuthContext.tsx`; `SessionReliabilityIntegrationTests` (17) | The hop-cap fix is TESTED only (not re-run at runtime) |
| **N5** | CORS | `CORS_ALLOWED_ORIGINS` actually bound; exact-origin validation (no `*`/`null`/wildcards); no credentials; required outside local | TESTED; RUNTIME; **BROWSER VERIFIED** (allowed vs untrusted origin, dev proxy flow) | `SecurityConfig`; `CorsPolicyIntegrationTests` (10), `CorsAllowedOriginsValidationTests` (14) | Production origin undecided (OD-12) |
| **N6** | Configuration binding | One contract: documented env names bind in every profile; missing/invalid values fail startup; relaxed-binding bypass variables refused | TESTED; RUNTIME VERIFIED (prod, staging, local) | `ConfigurationContract`, `SecurityProperties`, `MasteryProperties`, `application*.yaml`; `ConfigurationContractTests` (39) | `docker compose up` not run; `.env.example` stale (TD-02) |
| **3R** | Content lifecycle | **L4/L5 core fix accepted.** RETIRED = superseded; learner-visible = PUBLISHED or RETIRED; new work = current PUBLISHED only; existing path items and in-progress attempts keep their pinned version; history reconstructable | TESTED (8, with a mutation check); RUNTIME VERIFIED (journeys A–F) | `PublishableVersion.wasPublished`, `ActivityAttemptService.startAttempt`, `ContentLifecycleIntegrationTests` | **Broader version-consumer follow-up still open (3R.1).** Not browser-verified |
| **5D** | Core learning loop: server-authoritative post-completion | Completing an attempt, in one transaction under the attempt's row lock, records a REVIEW placement's outcome, re-evaluates Smart Review for every exercised skill, makes and records exactly one adaptive decision (`adaptation_decisions`, unique per attempt — V36), and places a due review. No client follow-up is needed (L8). Review items and streaks are created race-free with `INSERT … ON CONFLICT DO NOTHING` + row lock (N2, N8); answers take the attempt lock (N9). `/adaptive-next` and `/smart-review-check` became read-only compatibility views and the frontend no longer calls them. Golden journey rehabilitated (TD-03). Multi-skill decisions made deterministic under a provisional rule (L7 → PD-11) | TESTED (golden journey + 9 completion integration tests, 3 mutation checks); RUNTIME VERIFIED (journeys A–E on persisted state); **BROWSER VERIFIED** (UI play-through: complete, then reads only; reload resumes the next state) | `PostCompletionService`, `ActivityAttemptService.completeAttempt`, `SmartReviewService`, `ReviewItemRepository`, `LearnerStreakRepository`, `AdaptationDecision`, V36; `GoldenLearningJourneyIntegrationTests`, `LearningLoopCompletionIntegrationTests`; `PlaySession.tsx` | PD-11, PD-12, OD-10, TD-06, TD-08, TD-09 |
| **W1.1** | Wave 1 slice 1 — child & parent learning experience; content tenancy (N7) | **N7:** a path draws only from platform content (ownerless lesson/standalone activity) plus, for a teacher-owned curriculum, that owner's content — enforced in enrollment seeding, the Adaptive Engine, Smart Review and free practice; the family catalog and family enrollment accept platform curricula only; ADMIN-authored content is platform content. **Read APIs:** a child's enrollments (replacing the client-side pointer, N13), the journey with a per-step explanation derived from recorded decisions, skills with evidence level and standing (no raw mastery), activity history with the decision taken, child detail + rename, correct-answer feedback on recorded answers. **Frontend:** child space (journey home, activity intro, 4 question types, explanatory feedback, Learning Buddy, sound, completion with the server's next step, grown-up gate); parent family home and child overview rebuilt; enrollment pointer removed; AA contrast fixes | TESTED (ContentTenancyIntegrationTests 8 with 3 mutation checks; LearningJourneyApiIntegrationTests 5 over HTTP); RUNTIME VERIFIED (journeys A–E incl. N7 via real teacher authoring); **BROWSER VERIFIED** (desktop + 375 px mobile: family home, overview, journey, play MC/TF/numeric, wrong-answer feedback, reload resume, completion, keep going, keyboard-only play, grown-up gate, legacy route redirect) | `PathContentScope`, `LearningJourneyService`, `SkillProgressService`, `AnswerFeedbackService`, `EvidenceStandard`, `LearningJourneyController`; `features/child/*`, `features/parent/*`, `features/buddy/*` | PD-13–PD-17, TD-10–TD-13; frontend has no automated tests |
| **W1.2** | Wave 1 slice 2 — parent account & security settings | `GET /account` (own profile, never the hash); `POST /account/password` — current password required, a wrong one counts towards the login lockout (422 with a field error, then 423), policy-checked, must differ, then **every session is revoked** and a fresh one returned so this device stays signed in; `POST /account/sign-out-everywhere`; both audited (`sessionsEnded` = live devices, not chain rows). Frontend: Account page (details, change password, sign out everywhere with in-page confirmation), parent navigation that works on phones, login notice; login page lockout time made readable and link contrast fixed | TESTED (`AccountSettingsIntegrationTests` 5, HTTP); RUNTIME VERIFIED (password journey: wrong/weak/success, both old devices' refresh → 401, old password refused, audit row); **BROWSER VERIFIED** for the account page, sign-out-everywhere (other device's refresh → 401 afterwards) and mobile navigation. **Password form submission NOT browser-verified**: entering passwords in the browser is outside what the agent may do — verified over HTTP only | `AccountSettingsService`, `AccountController`, `AccountSessionRepository.revokeAllForAccount`; `AccountPage.tsx`, `ParentLayout.tsx`, `AuthContext` | PD-02 (≤ 15 min access-token tail, stated in the UI); PD-18; password reset/verification remain Wave 4 |
| **W2.0** | School ↔ family boundary (N14, TD-12) | **N14:** any teacher, principal or admin could put any family's child into a class by id (`SchoolService.enrollChild` checked only that the child existed) and then assign any curriculum — including another teacher's — which enrolled the family child and routed teacher content into the family path around N7. No relationship yet records a family authorizing a school (PD-01), so staff enrollment is now **refused for every role, before any lookup** (no child-existence oracle). A class curriculum must be published platform content or the assigner's own (TD-12), assigned in one transaction | TESTED (`SchoolFamilyBoundaryIntegrationTests` 3, HTTP; all 3 failed on the old code first); RUNTIME VERIFIED (admin-provisioned teacher/principal, real school + class: teacher, principal and admin → 403; unknown child → same 403; roster empty) | `SchoolService.enrollChild`/`assignCurriculum`, `SchoolController` | **PD-01** — the school product cannot enroll children until the family-authorization model is decided; TD-14 (legacy class rows), TD-17 |
| **W4.1** | Rate limiting (OD-01), session resilience, frontend test harness | Per-client-address token buckets on the unauthenticated identity endpoints: login 20/min, register 20/h, refresh + logout 60/min (env-configurable, N6 contract). 429 problem+json with `Retry-After`, inside the chain after CORS; refused **before** the controller so a refused refresh never rotates the token; decoded-path matching (an encoded `/auth/%6Cogin` counts). Frontend: a 429 refresh keeps the session (it previously would have signed the user out); a start-up refresh that cannot complete shows "We couldn't reach Bookeyond Kid — you're still signed in" with Try again instead of the log-in page; login/register show the wait. Vitest + Testing Library introduced. Register page no longer promises an approval email that nothing sends | TESTED (`TokenBucketLimiterTests` 4; `AuthRateLimitIntegrationTests` 4, HTTP; `ConfigurationContractTests` +3 variables; Vitest 13; mutation checks on path decoding and on the client's 429 handling); RUNTIME VERIFIED (login 401×3 → 429 `Retry-After: 20`; refused refresh then 200 with the same token after refill); **BROWSER VERIFIED** (spent refresh budget → still-signed-in screen, token kept; Try again after refill → family home, token rotated; 375 px layout) | `security/ratelimit/*`, `SecurityConfig`, `application.yaml`; `client.ts`, `AuthContext.tsx`, `RequireAuth.tsx`, `ApiError.ts`; `vitest.config.ts` | Per-instance budgets (OD-12); TD-15 (forwarded-address trust); the login/register 429 message is TESTED, not browser-verified (would require typing a password); TD-16 |
| **4C** | School ↔ child authorization | **NOT IMPLEMENTED** — design only | — | Battle 4C design report | PD-01 |

---

## Open findings register

| ID | Finding | Status | Evidence | Next dependency |
|---|---|---|---|---|
| **3R.1** | Remaining version consumers after 3R: challenge lifecycle (OD-09), adaptive version-completion (OD-10), lesson/assessment lifecycle audit (OD-08), localization/media ↔ version relationships, withdrawal semantics (PD-04) | OD / PD | 3R report §7, §14 | PD-04 before any Content Studio work |
| **L7** | Multi-skill activities: decisions used an unordered `get(0)` skill | **Technically resolved (5D); policy open** | Review is evaluated for every exercised skill; the single adaptive decision is governed by the lowest-mastery exercised skill (ties by id) and the decision context records it. Integration test + runtime journey E | PD-11 |
| **L8** | Adaptive and Smart Review decisions ran only if the client called `/adaptive-next` and `/smart-review-check` | **RESOLVED (5D)** | Decisions happen inside completion; golden journey and completion tests call nothing else; the browser makes no follow-up calls | — |
| **N2** | Smart Review find-or-create race (concurrent evaluations failed on the duplicate key) | **RESOLVED (5D)** | `INSERT … ON CONFLICT (child_id, skill_id) DO NOTHING` + `FOR UPDATE`; 8-way concurrency test; the mutation check fails it | — |
| **N8** | Streak find-or-create race inside completion: concurrent first completions for one child failed, and concurrent updates hit the version check | **RESOLVED (5D)** | Found in 5D; same insert-if-absent + row lock | — |
| **N9** | An answer could be written after completion had scored the attempt (no shared lock) | **RESOLVED (5D)** | Answer submission and completion both take the attempt row lock; the answer re-checks IN_PROGRESS | — |
| **L9** | MATCHING has no scoring or interaction; refused at publish | FP / PD | `QuestionService.validateCompleteness`; `AnswerScoringServiceTests.matchingIsNeverScored`; frontend renders none | Interaction architecture phase |
| **N7** | Teacher-authored content tagged to a system skill is placed into **every** family child's path for that skill | **RESOLVED (W1.1)** — PD-05 answered by the product directive §14.1 ("a teacher's content MUST NOT accidentally enter unrelated family children's learning paths; enforce on the server") | `PathContentScope` applied at every selection point; 8 integration tests, each filter mutation-checked; runtime: a teacher's published activity on a platform skill reaches 0 family paths, teacher curriculum enroll/free practice → 404 | — |
| **N10** | A teacher publishing a curriculum for an age group silently stopped auto-enrollment for every new family child of that age (two candidates = ambiguous) | **RESOLVED (W1.1)** | Found during W1.1 runtime; auto-enrollment considers platform curricula only | — |
| **N11** | The family catalog listed curricula with no published version, so "Start" failed | **RESOLVED (W1.1)** | `CurriculumController` filters to published platform curricula | — |
| **N12** | Primary buttons used white on #3B82F6 (3.7:1), failing WCAG AA for normal text | **RESOLVED (W1.1)** | `brand.blueStrong` #2563EB (5.2:1); child palette deep tones checked ≥ 4.9:1 on their fills | — |
| **N13** | Enrollment discovery lived in browser localStorage: progress was invisible on another device or browser, and the enrollment created automatically with a child was never reachable | **RESOLVED (W1.1)** | `GET /children/{id}/enrollments`; pointer store deleted, stale key cleared on load; browser-verified | — |
| **TD-10** | ADMIN-authored content is now ownerless platform content; rows an ADMIN authored before W1.1 still carry the admin's id and are treated as that admin's private content | TD | `TeacherAuthoringController.ownerFor` | Data audit before any production data migration |
| **TD-11** | Class challenges pin an activity version; with N7 a teacher's challenge activity can be started (free practice) only by children enrolled in that teacher's curriculum | TD / W2 | `PathContentScope.admitsActivityForChild` | Wave 2 challenge + class scope |
| **TD-12** | `SchoolController.assignCurriculum` lets a class teacher assign any curriculum, including another teacher's | **RESOLVED (W2.0)** | Platform-published or the assigner's own only; integration test | — |
| **N14** | Any school staff member could enroll any family child into a class by id, then route any curriculum (and so teacher content) into that child's learning | **RESOLVED (W2.0)** — refused for every role until PD-01 defines family authorization | `SchoolFamilyBoundaryIntegrationTests`; runtime | PD-01 |
| **TD-14** | `class_enrollments` rows created before W2.0 carry no family authorization, yet still feed the teacher roster, class curriculum assignment and challenge progress | TD (data) | V24 schema; no production data exists yet | Data audit before any production data; resolve with PD-01 |
| **TD-15** | Rate limits key on `getRemoteAddr()`. The prod profile uses `forward-headers-strategy: framework`, which trusts X-Forwarded-For as sent — behind a proxy that appends rather than replaces it, a client can choose its own address and evade the limit | TD / OD-12 | `application-prod.yml`; `AuthRateLimitFilter` javadoc | Deployment topology: proxy must overwrite X-Forwarded-For, or switch to `native` with trusted proxies |
| **TD-16** | Frontend dependency advisories: `vite` 5 (dev-server path traversal), `react-router-dom` 6 (open redirect via backslash in `<Link>`/`navigate`; fix only in v7), `vitest` 2 (UI-server advisory; the UI is not used) | TD | `npm audit` (2026-09-26) | Planned upgrade (Vite + React Router majors) |
| **TD-17** | School staff scope is wider than the school: a principal may assign **any** TEACHER account on the platform to a class (teachers are not linked to schools), and `ContentAccessGuard` lets any PRINCIPAL write any teacher's content | TD / PD-01 | `SchoolService.assignTeacher`, `ContentAccessGuard.requireOwnerWrite` | School staff membership is part of the PD-01 model |
| **TD-13** | `GET /activity-versions/{id}` stays unscoped: any authenticated user can read any learner-visible version, including teacher content (answer keys are never included) | PV by design / W2 | `ActivityContentController` | Revisit with school tenancy |
| **TD-03** | Golden learning journey test was stale (answered 1 of 2 required questions; drove decisions by hand) | **RESOLVED (5D)** | Rewritten to play each path through next → start → answer all → complete and assert only persisted state; green | — |
| **TD-04** | PRINCIPAL registration test expects rejection; `AuthController` returns 202 pending approval | TD + PD-07 | `PublicRegistrationRoleSecurityTests.registrationRequestingPrincipalIsRejected` | PD-07 |
| **OD-01** | No rate limiting anywhere (refresh, login, register) | **RESOLVED (W4.1)** for the unauthenticated identity endpoints; per instance only | `AuthRateLimitFilter`; tests + runtime + browser | Shared store when there is more than one instance (OD-12) |
| **PD-02** | Access tokens stay valid for up to 15 min after logout/revocation/disable (stateless JWT) | PD | 4D §13 | Product/security decision |
| **TD-05** | `account_sessions.replaced_by_session_id` has no index; family walks are one query per link | TD | 4D §13; migrations | Index migration (small) |
| **OD-12** | Production CORS / deployment topology undecided: no production frontend origin; compose `frontend` builds from nonexistent `../YulMe-Frontend`; no production frontend serving config; staging lacks `forward-headers-strategy` | OD / PD | N5 §2, §9 | Deployment decision |
| **TD-02** | `backend/.env.example` is stale (JWT_ISSUER shown optional, JWT_SECRET/JWT_REFRESH_TOKEN_TTL/DB_USERNAME/DB_PASSWORD missing, CORS default wording) | TD | N6 §9; **the file is blocked by the user's permission settings** | User edit or permission change |
| **TD-06** | Threshold/configuration drift: `AdaptiveEngine` hard-codes 0.4/0.8 and `MIN_EVIDENCE_*` (also `SmartReviewService` 0.5/2 and `ParentInsightService`), while `MASTERY_OBJECTIVE_THRESHOLD` is configurable and unlinked | TD / PD — reviewed in 5D: distinct concepts (skill-level direction vs objective mastery), no correctness defect in the completion decisions, left unchanged | Source constants | Config follow-up if thresholds become operator-tunable |
| **PD-04** | Withdrawal / unsafe-content semantics: no way to pull a wrong or harmful version; RETIRED only means superseded, so a wrong answer key keeps being served wherever pinned | PD | 3R §7 #7, §13 | Product decision |
| **OD-08** | Lesson and assessment versions share the lifecycle; their learner consumers are not audited | OD (unaudited) | 3R §14 | 3R.1 |
| **OD-09** | Challenges pin one exact ActivityVersion; progress counts only that version, so it stalls once children move on to v2 | OD / PD | `Challenge.activityVersionId`, `ChallengeService.progress` | PD: count by activity or by version |
| **OD-10** | Adaptive treats "completed" per version id: a child who finished v1 can be recommended v2 of the same activity | OD / PD — **left unchanged in 5D**: not inseparable from the loop, and changing it would set content-version policy | `AdaptiveEngine.pickActivity` (`completedActivityVersionIds`) | Product decision |
| **TD-01** | Workspace was not under version control | **RESOLVED** | Git baseline `4db89ad` | — |
| **TD-08** | The compatibility views keep their POST mappings although they now only read | TD (minor) | `ActivityAttemptController` | Convert to GET, or remove once no client needs them |
| **TD-09** | A REVIEW path item doesn't record which review item it served; completion chooses one deterministically (ACTIVE, then earliest due, then skill id) — exact for single-skill activities | TD | `SmartReviewService.recordReviewCompletion` | Link placement to review item if multi-skill review becomes common |
| **TD-07** | Resuming an existing attempt answers 201 Created instead of 200 | TD (minor) | 3R runtime journey C | Contract cleanup when attempt APIs are next touched |

---

## A. Architecture baseline

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Stack | VC | Spring Boot 4.1.1 / Java 21, PostgreSQL 18, Flyway (V1–V35), JPA `ddl-auto: validate`; Vite + React 18 + TypeScript + Tailwind frontend | `pom.xml`, `application.yaml`, `frontend/package.json` | — | — | — |
| Backend authority | VC | Scores, evidence, mastery, adaptive/review decisions, lifecycle and authorization are server-side; completion itself triggers the decisions | Code across `learning`, `learner`, `review`, `parent`; 5D tests + browser | 2A–5D | — | — |
| Versioned, pinned content | VC | Immutable published versions; learner references pin exact ids (RESTRICT FKs) | 3R reconstruction | 3, 3R | 3R.1 | — |
| Observability | PV | Correlation-id filter + MDC in logs; actuator health/info; audit events | `CorrelationIdFilter`, `CorrelationIdFilterTests` (2), `AuditService` tests | — | No metrics/alerting/production log strategy | Release-readiness phase |
| Version control / checkpoints | VC | Git at the workspace root; baseline `4db89ad`, one commit per battle | `git log` | — | No remote backup configured | User decision |

## B. Identity / authentication

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Register / login / refresh / logout | VC | `/api/v1/auth/*`; PARENT self-registration; lockout on failed logins | `SessionReliabilityIntegrationTests`; N6 runtime (lockout after N attempts) | 4A, 4D, N6 | — | — |
| Session state machine | VC | Hashed single-use refresh tokens, rotation, replay → family revocation, logout, disabled account refused | 17 integration tests; runtime + browser (4D) | 4A, 4D | Hop-cap fix TESTED only | — |
| Frontend session contract | VC | 401 → one serialized refresh → one retry; 403 never refreshes; "unavailable" (incl. 429) keeps the token; a start-up refresh that cannot complete shows a still-signed-in retry screen, never the log-in page | Browser verified (4D, W4.1); Vitest (client + RequireAuth) | 4D, W4.1 | Browsers without Web Locks fall back to in-tab coalescing only | — |
| Rate limiting | PV | Per-address budgets on login/register/refresh/logout; 429 + Retry-After | Tests, runtime, browser (W4.1) | W4.1 | Per instance (OD-12); forwarded-address trust (TD-15); authenticated endpoints unlimited | Deployment decision |
| Access-token revocation latency | PD | ≤ 15 min after logout/disable/role change | 4D | 4D | PD-02 | — |
| Refresh token storage | TD | Refresh token in `localStorage` (XSS exposure) | `tokenStore.ts` | 4D | Hardening option: httpOnly cookie + CSRF design | Security hardening |
| Email / phone verification | FP | `Account.emailVerifiedAt` exists; no verification flow or endpoints | `AuthController` has only register/login/refresh/logout | — | Real tokens, expiry, resend limits (`CLAUDE.md` §11) | Identity phase |
| Password change | VC | Current password required, lockout-counted, all sessions revoked, fresh session issued, audited | 5 integration tests; runtime; UI rendered (submission verified over HTTP only) | W1.2 | — | — |
| Sign out everywhere | VC | Revokes every live session of the account; audited | Integration test; runtime; **browser** | W1.2 | Access tokens live ≤ 15 min (PD-02) | — |
| Password reset | FP | None — needs real email delivery (CLAUDE.md §11) | `AuthController` | — | Whole flow | Wave 4 identity |
| PRINCIPAL self-registration | PD | 202 pending ADMIN approval; approve endpoint exists | `AuthController`, `AdminController`; TD-04 | — | Stale test contradicts code | PD-07 |
| Staff provisioning / bootstrap admin | PV | ADMIN creates TEACHER/PRINCIPAL; optional bootstrap admin via `platform.bootstrap-admin.*` | Exercised at runtime in 3R | — | No automated tests; bootstrap vars aren't in the N6 contract (documented as YAML only) | Admin phase |

## C. Authorization

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Family → child access | VC | `FamilyAccessGuard` on child-scoped endpoints | 4D test (stranger → 403); 3R runtime (other child's path item → 403) | 2A, 4D | — | — |
| Content write authorization | PV | `ContentAccessGuard`: owner, or ADMIN/PRINCIPAL; system content ADMIN-only | Code; positive runtime path (3R) | 4B | No test for the TEACHER-refused case | Test |
| School ↔ child authorization | PD | No relationship records a family authorizing a school, so staff enrollment of a child is **refused for every role** (W2.0); class curricula limited to platform or own | 3 integration tests; runtime | 4C, W2.0 | **PD-01**: the school authorization model; TD-14, TD-17 | Product decision, then implementation |
| Content catalog reads | PV (by design) | `GET /activity-versions/{id}` is not child-scoped: any authenticated user reads any learner-visible version | `ActivityContentController` javadoc | 3, 3R | TD-13 | Wave 2 |
| Teacher content reaching family children | VC | N7 resolved: `PathContentScope` at every selection point; family catalog/enrollment platform-only | 8 integration tests + mutation checks; runtime | W1.1 | Class-side scoping (TD-11, TD-12, TD-13) | Wave 2 |

## D. Learning Path

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Path seeding | VC | Curriculum objectives → skills → current PUBLISHED activity versions within the path's content scope (N7) | `EnrollmentService.seedInitialPath`; 3R tests; `ContentTenancyIntegrationTests` | 2B, 3R, W1.1 | — | — |
| Journey read model | VC | Enrollments, journey steps with explanation from recorded decisions, history with decision, skills with evidence standing | `LearningJourneyApiIntegrationTests` (5, HTTP); runtime A–B; browser | W1.1 | Explanation of a REVIEW step names the first reviewed skill on it (TD-09) | — |
| Next activity / continuity | VC | First PENDING/IN_PROGRESS item; pinned versions stay playable after republish | 3R journeys A, C | 3R | — | — |
| Concurrent insertion | VC | Row lock on the path for every insertion | 5A tests | 5A | — | — |
| Auto-enrollment | PV | Only when exactly one curriculum matches | Code only | 2B | No automated test | Test |

## E. Activities / attempts

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Start / resume / answer / complete / abandon | VC | Server-scored; one IN_PROGRESS attempt per child+version; resume returns the same attempt; completion and answers serialize on the attempt row lock; completion is idempotent under retry and concurrency | `ActivityAttemptServiceTests`; 3R journeys; 5D completion tests; runtime D1 (6 simultaneous completions → one decision) | 2A, 5A, 3R, 5D | Resume answers 201, not 200 (TD-07) | — |
| Answer contract | VC | MC = option id; canonical TF/NUMERIC; SHORT_ANSWER trimmed and case-insensitive; invalid → 422 | `AnswerScoringServiceTests` (7) | 5A | L9 | — |
| Required-question completion | VC | Completion refused until every item is answered | `completeAttemptRejectsAnAttemptWithMissingRequiredAnswers`; the golden journey answers every question | 5A, 5D | — | — |
| Duplicate answers / idempotency | VC | Idempotency guard + unique answer per attempt+question (V30) | `IdempotencyServiceTests` (11) | 2D | — | — |

## F. Evidence / mastery

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Skill mastery | VC | Bias-corrected EWMA; configurable rate/saturation (validated at startup) | 5B tests; N6 runtime (rate 0.5 → 0.3333 exactly) | 5B, N6 | — | — |
| Objective rollup | VC | Average over observed skills; coverage and evidence strength separate | 5C tests; V35 | 5C | Weighted rollup is a possible future refinement | — |
| `confidence` field | TD | Computed and stored on `LearnerSkillState`; **never read** by any decision | No `getConfidence()` caller outside the entity | — | Use it or remove it | PD |
| Skill mappings unversioned | PD | Tags live on the Activity/Question, not on versions | 3R §2 | 3R | Retagging changes meaning across all versions (PD-08) | Product decision |

## G. Adaptive Engine

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Recommendation | VC | Decided on completion, exactly once per attempt and recorded (`adaptation_decisions`, V36): REINFORCEMENT / CHALLENGE / CONTINUE from mastery + evidence gates, current PUBLISHED versions, reusing a waiting placement rather than duplicating it | Golden journey (A challenged, B reinforced on the prerequisite); completion tests; runtime A–E; browser | 5A, 5B, 3R, 5D | Multi-skill policy (PD-11); version-completion (OD-10); TD-06 | PD-11 |
| End-to-end loop coverage | VC | The golden journey plays two learners' paths through the real services and is green | `GoldenLearningJourneyIntegrationTests`; fails when completion's decisions are removed (mutation check) | 5D | — | — |

## H. Smart Review

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Flag / schedule / insert / outcome | VC | Run by completion for every exercised skill; race-free item creation (N2); a REVIEW placement's outcome recorded on completion; due review inserted under the path lock; current PUBLISHED versions only | Cold-start tests; 3R test; 5D concurrency tests (8-way evaluation → one item; concurrent completions → one item, one pending REVIEW); runtime D2/D3 | 2D, 5A, 3R, 5D | Free practice doesn't evaluate review (PD-12); TD-09 | PD-12 |

## I. Progression

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Progress summary / streaks | PV | Completed activities, minutes, streak, path counts; the streak is updated race-free inside completion (N8) | `LearnerProgressController`, `StreakController`; 5D concurrency tests exercise the streak path | 5D | No dedicated progress/streak tests; no progression model beyond counts | Child/parent product phases |

## J. Parent Insight

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Current vs history, evidence-grounded claims | VC | Live current interpretation; `generate` records snapshots as history | 16 tests (2C/5C) | 2C, 5C | English-only insight text; UI doesn't show coverage/history (PD-06) | Parent product phase |

## K. Content / version lifecycle

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Activity/Question versions | VC | DRAFT → PUBLISHED → RETIRED (superseded); `wasPublished()`; new work = current PUBLISHED | 8 tests + mutation check; runtime journeys | 3, 3R | 3R.1 | — |
| Withdrawal of unsafe content | PD | Does not exist | 3R | 3R | PD-04 | Product decision |
| Question change propagation | PD | Republishing a question doesn't change activities; the author must publish a new activity version | 3R §7 | 3R | Authoring UX to flag activities pinning superseded questions | Content Studio |
| Lesson / assessment / challenge / localization consumers | OD | Not audited (lesson/assessment); challenge pins exact version | 3R §14 | — | OD-08, OD-09 | 3R.1 |
| Legacy data risk | NOT VERIFIED | Versions published before Battle 3's check could pin DRAFT questions (start OK, content 404) | Not found in seed data; real databases not checked | 3 | Data audit | Before production data migration |

## L. Interaction architecture

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Question types | PV | MC, TRUE_FALSE, NUMERIC, SHORT_ANSWER scored and rendered | `AnswerScoringService`, `QuestionRenderer.tsx` | 5A | MATCHING (L9); no drag/drop, audio-first or other child-native interactions | Interaction architecture phase |
| Localization / audio / media | NOT VERIFIED here | Accepted in Battle 3's scope | Not re-inspected in this reconciliation | 3 | Relationship to versions (3R.1); no language selector behavior verified | Audit |

## M. Challenges / Buddy

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Challenges | PV | Staff create class challenges on a PUBLISHED version; child progress for class-assigned children | `ChallengeService`, controllers | — | OD-09; no tests; no child UI | PD on counting |
| Learning Buddy | PV | See Q (child space) | Browser (W1.1) | W1.1 | PD-13 | — |

## N. CORS / configuration

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| CORS | VC | Exact origins from `CORS_ALLOWED_ORIGINS`; required outside local | 24 tests; runtime + browser | N5 | OD-12 | Deployment decision |
| Configuration contract | VC | Documented names bind everywhere; fail-loud validation; bypass variables refused | 39 tests; runtime in 3 profiles | N6 | TD-02 (`.env.example`); compose not run | — |

## O. Subscription / entitlements

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Subscription, plans, entitlements, payment | FP | **Nothing exists** | No domain, table or endpoint found | — | Entire capability; business model undefined (PD-09) | Product decision |

## P. Parent product

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Parent web app | PV | Family home (per-child current path + next step); child overview: learning now with "why this next", full path with reasons, skills & evidence (standing + evidence level, never "weak" for "not assessed"), recent activity with the decision taken, current insights with their evidence basis, rename, add a subject | **BROWSER VERIFIED (W1.1)** desktop + mobile | W1.1 | Account/settings, password change/reset, goals, insight history (PD-06 partly: coverage and evidence strength are now shown), consent, Khmer UI (PD-14) | Wave 1 next slices; Wave 4 identity |
| Consent | PD / FP | `ConsentRecord`/`ConsentService` exist, **not wired** | `ChildService` javadoc | — | Real consent workflow | Product/legal decision |

## Q. Child product

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Child space | PV | `/learn/:childId`: journey home (greeting, Learning Buddy, progress, one Play action, journey map of completed/current/upcoming steps with Review/Challenge/Practice tags, subject switcher); play session: activity intro with the reason, MC/TF/numeric/short answer with number-key shortcuts, explanatory feedback (correct answer after a miss), buddy reactions, synthesized sound with mute, completion with correct count and the server's next step + reason; grown-up press-and-hold gate. Resume, idempotency and conflict recovery from 5D kept | **BROWSER VERIFIED (W1.1)**: desktop + 375 px, keyboard-only play, wrong-answer feedback, reload mid-activity resumes at the next unanswered question | 3R, 5D, W1.1 | Audio/media content, richer interactions (L9, drag/drop, tracing), buddy name/design sign-off (PD-13), Khmer UI (PD-14), automated UI tests | Content (PD-15), interaction phase |
| Learning Buddy | PV | Authored SVG character (mint sprout with a book), moods idle/happy/cheer/encourage/thinking; speaks only product-written lines chosen from real state; reduced-motion respected | Browser (W1.1) | W1.1 | Name and character sign-off (PD-13); more authored guidance moments | PD-13 |

## R. Admin / content platform

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Authoring API | PV | Lessons, activities, questions, curricula, versions, publish (`/api/v1/teacher/*`) | Exercised at runtime in 3R | 3, 4B, 3R | No authoring tests beyond lifecycle; PD-04 | Content Studio |
| Admin API | PV | Staff provisioning, principal approval | Runtime (3R) | — | Admin console UI is a "ComingSoon" placeholder | Admin phase |
| Content Studio UI | FP | None | `App.tsx` | — | Entire UI | Content Studio phase |

## S. School foundation

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Schools, classes, teacher assignment, class enrollment, class curricula | PV | `SchoolController` + `SchoolAccessGuard` (assignment-scoped teacher access) | Code; no automated tests | 4C | **PD-01** school ↔ child authorization; principal UI is a placeholder; teacher roster UI exists | PD-01 |

## T. Frontend

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Build / lint | PV | `tsc -b` + `vite build` pass; lint 0 errors (1 pre-existing warning) | Re-run in 5D after the `PlaySession` change | 4D, 5D | No automated frontend tests | Testing phase |
| Auth / session | VC | See B | Browser (4D) | 4D | — | — |
| Automated tests | PV | Vitest + Testing Library (jsdom), `npm test`: API client session handling, rate-limit copy, RequireAuth start-up states (13) | `vitest.config.ts`, `*.test.ts(x)` | W4.1 | Child play session, parent overview, Studio; no E2E | Next slices |
| README accuracy | VC | Rewritten to current status in W1.1 | `frontend/README.md` | W1.1 | — | — |
| Accessibility | PV | AA contrast on controls and child palette; labelled form fields with linked errors; focus moves to the next action after feedback/completion; keyboard-only play; aria-live status for feedback; reduced motion | Browser (W1.1); computed contrast ratios | W1.1 | No automated a11y checks; screen-reader pass not performed | Testing phase |

## U. Testing / verification

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Backend suite | PV | 177 tests (unit + Testcontainers integration); 176 pass | Last run 2026-09-25 (5D) | all | TD-04 red (PD-07); 2B, 4B, challenges, school, progress untested | PD-07; test backfill |
| CI | TD | **None** (no `.github` or other pipeline) | Repository | — | Every verification is manual | TD-01 then CI |
| E2E browser automation | FP | Browser verification is manual per battle | Battle reports | — | Automated critical-flow E2E | Testing phase |

## V. Security hardening

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Implemented | VC | Lockout, hashed rotating refresh tokens, replay detection, 401/403 semantics, exact-origin CORS, fail-loud config, draft-content isolation, family access guards | Battles 4A–N6, 3R | — | — | — |
| Open | OD / PD | Revocation latency (PD-02); refresh token in `localStorage`; `replaced_by` index (TD-05); no identity verification; no password reset; 4B TEACHER-refused case untested; forwarded-address trust (TD-15); dependency advisories (TD-16); school staff scope (TD-17) | See registers | — | — | Security hardening |
| Rate limiting / school boundary | PV | See B and C (W4.1, W2.0) | — | W2.0, W4.1 | — | — |

## W. Product-completion gaps

Child experience, parent dashboard depth, Learning Buddy, interaction types beyond four
question types, school authorization and consoles, admin console and Content Studio,
identity verification and password flows, consent, subscription, production deployment
and CI. None of these is complete; see sections B, L, M, O–U.

---

## Product decisions register

| ID | Decision | Raised in |
|---|---|---|
| PD-01 | School ↔ child authorization model (school authorization, class enrollment, teacher assignment as distinct relationships) | 4C |
| PD-02 | Accept ≤ 15 min access-token validity after logout/disable, or pay for immediate revocation | 4D |
| PD-03 | Strict single-use refresh (current) vs a grace window for legitimate concurrent refreshes | 4D |
| PD-04 | Withdrawal / unsafe-content state distinct from "superseded" | 3R |
| PD-05 | Whether teacher-authored content may enter family (non-school) curricula at all (N7) — **answered by the product directive §14.1: it must not**; implemented in W1.1 | Battle 5 recon; observed again in 3R |
| PD-06 | Parent UI display of coverage, evidence strength and insight history | 5C |
| PD-07 | PRINCIPAL self-registration: pending approval (code) vs rejected (stale test) | Earlier battle reports (stale test) |
| PD-08 | Versioned vs identity-level skill mappings | 3R |
| PD-09 | Subscription / entitlement business model | — |
| PD-10 | Unstarted path items on a superseded version: keep pinned (current) vs an explicit refresh rule | 3R |
| PD-11 | Multi-skill activities: one adaptive decision governed by the weakest exercised skill (provisional, current) vs one decision per skill vs a designated primary skill | 5D |
| PD-12 | Whether free-practice completions (no path item) should evaluate Smart Review; today they record evidence only, as before 5D | 5D |
| PD-13 | Learning Buddy name and character design sign-off — the W1.1 character is an authored proposal, unnamed in the product | W1.1 |
| PD-14 | Khmer UI localization: needs native-speaker-authored copy (machine-produced Khmer for children is not acceptable); the UI is English-only today | W1.1 |
| PD-15 | Canonical curriculum content (Khmer, English, more Maths) must come from educators — directive §10 forbids generative AI determining canonical curriculum. The catalog is one Maths curriculum of 6 numeric activities | W1.1 |
| PD-16 | After a wrong answer the child is shown the correct answer (directive §12.4 "explanatory"). Alternative: one retry before revealing | W1.1 |
| PD-17 | Grown-up gate style (press-and-hold 1.5 s) for leaving the child space | W1.1 |
| PD-18 | Privacy notice and Terms of Service text — legal content, not engineering; no page is shown until it exists (a placeholder would be fake) | W1.2 |

---

# CURRENT VERIFIED PROJECT STATE

- **Backend foundation integrity baseline: established.** Accepted and evidenced: 2A, 2B
  (inspected only), 2C, 2D, 3, 4A, 4B (partial), 4.1, 5A, 5B, 5C, 4D, N5, N6, 3R
  (L4/L5 core), **5D (server-authoritative learning loop)**, and **W1.1 (child & parent
  learning experience; N7 tenancy)**.
- **Highest evidence:** the session/auth flow, CORS and learning-loop completion are
  BROWSER VERIFIED; configuration and content lifecycle are RUNTIME VERIFIED; the
  learning-state battles (5A–5C) were RUNTIME VERIFIED at battle time and are TESTED now,
  including through the green golden journey.
- **Test suite:** backend 210 run, 209 pass (the 1 failure is the known stale PRINCIPAL test, TD-04 / PD-07); frontend (Vitest) 13 pass.
- **Version control:** git, one commit per battle.
- **FULL BOOKEYOND KID PRODUCT: NOT COMPLETE.**

# REMAINING ENGINEERING GAPS

1. Learning-loop policy left open by 5D: multi-skill decisions (**PD-11**), free-practice
   review (PD-12), version-completion (OD-10), thresholds (TD-06); TD-08, TD-09.
2. PRINCIPAL test red (TD-04, needs PD-07).
3. Tenancy follow-ups on the school side (TD-11, TD-12, TD-13 — Wave 2).
4. Version consumers beyond activities/questions (**3R.1**: OD-08, OD-09, localization/media).
5. Security hardening: **rate limiting (OD-01)**, `replaced_by_session_id` index (TD-05),
   token storage, identity verification, password reset, 4B negative test.
6. Deployment: production origin/topology (OD-12), compose frontend context, staging
   forwarded headers, stale `.env.example` (TD-02).
7. Engineering infrastructure: no CI, no remote backup of the git repository, no frontend
   tests, no automated E2E; untested areas (2B auto-enrollment, school, challenges,
   progress/streaks).
8. Dead or unlinked state: `confidence` never read; hard-coded thresholds.

# PRODUCT GAPS

Child experience (UX, Learning Buddy, motion, audio, accessibility), parent dashboard depth
(coverage/history, goals), consent workflow, school domain (PD-01) and principal/teacher
consoles, admin console and Content Studio, interaction types (MATCHING and child-native
interactions), identity verification and password flows, subscription/entitlements, and
the open product decisions PD-01 to PD-10.

# FUTURE PHASES

1. **Learning-loop policy:** decide PD-11, PD-12 and OD-10, then small follow-up battles.
2. **Security hardening:** rate limiting, revocation strategy, token storage, identity
   verification, password reset.
3. **Content platform:** 3R.1, withdrawal semantics, Content Studio, localization/media.
4. **Child product,** then **parent product** depth.
5. **School domain** after PD-01.
6. **Release readiness:** git, CI, frontend and E2E tests, deployment topology, observability.
7. **Subscription/entitlements** after PD-09.

# IMMEDIATE NEXT WORK

**Wave 1 continues** (child + parent), in this order:

1. ~~Parent account & settings~~ — **done in W1.2** (profile, password change, sign out
   everywhere). Still open: privacy/terms text (PD-18), child archive (needs a product
   decision on what archiving does to learning and data — not invented).
2. **Parent goals** (directive §13.5) — needs a product decision on what a goal is (e.g.
   minutes per week vs skills to secure) before any code; do not invent it.
3. **Automated frontend tests** for the child play session and parent overview (none exist).
4. Content: nothing further can be built without educator-authored content (PD-15) and
   Khmer copy (PD-14); Content Studio (Wave 3) is the tool that unblocks both.

Security hardening that needs no product decision (rate limiting OD-01, TD-05 index) remains
the fallback whenever Wave 1 is blocked on a decision.
