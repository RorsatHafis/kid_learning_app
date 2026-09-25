# Bookeyond Kid — Engineering Progress (canonical)

**The single source of truth for engineering progress across sessions.**
`CLAUDE.md` is the engineering constitution and governs; this document records where the
project actually stands against it. Where the two disagree on a verification level, the
evidence recorded here is newer (see §26 note under Battle 4A) — `CLAUDE.md` is not edited
from here.

- **Last reconciled:** 2026-09-25, after Battle 3R.
- **Reconciled against:** current source, Flyway migrations V1–V35, the last full test run
  (`./mvnw -Pintegration test`: **168 run, 166 pass, 2 known stale failures**), and the
  runtime/browser evidence gathered in each battle.
- **Not under version control:** neither `D:\Bookeyond-Kid` nor `backend/` is a git
  repository. The only checkpoint is `Bookeyond-Kid-Backend-Battle4-SecurityBoundaryIntegrity.zip`
  (Battle 4). Everything since exists only in this working tree — see TD-01.

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

**BACKEND FOUNDATION: integrity baseline established — not production-complete.**
The core learning loop (content → path → attempt → evidence → mastery → adaptive/review →
parent insight), authentication/session state, configuration and content versioning have
each been hardened and verified to the levels below. Known open defects remain in the
post-completion loop (L8, N2, L7), tenancy (N7) and security hardening (rate limiting).

**FULL BOOKEYOND KID PRODUCT: NOT COMPLETE.**
The child experience, parent dashboard depth, school domain authorization, admin/content
studio, identity verification, subscription and deployment are unbuilt or partial. Do not
describe the product as complete because the backend integrity battles are.

---

## Accepted battle register

| Battle | Scope | What was established | Highest verification | Evidence | Residual |
|---|---|---|---|---|---|
| **2A** | Learning-path / attempt integrity | An attempt's path item must belong to the child and pin the same ActivityVersion ("Known Backend Fix #2/#2b") | TESTED (unit); RUNTIME VERIFIED (3R: another child's path item → 403) | `ActivityAttemptService.requireLearningPathItemBelongsToChild`; `ActivityAttemptServiceTests` (3 start cases) | Verification level at original acceptance not recorded in the repo |
| **2B** | Auto-enrollment | Best-effort enrollment when exactly one ACTIVE curriculum matches the child's age hub; never a fabricated default | INSPECTED only | `ChildService.completeAutoEnrollment`, `EnrollmentService.autoEnrollIfUnambiguous` | **No automated test** (no test references `autoEnroll`) → PV |
| **2C** | Evidence-strength Parent Insight | Claims require minimum evidence; weak evidence is not shown as mastery | TESTED | `ParentInsightService` (`MIN_EVIDENCE_*`); `ObjectiveInsightTruthfulnessTests` (12), `ParentInsightTruthfulnessIntegrationTests` (4) | Extended by 5C |
| **2D** | Adaptive / Smart Review transactional reliability | Answer persistence, evidence, mastery and the idempotency guard commit in one transaction | TESTED (idempotency, persistence) | `ActivityAttemptService`/`AnswerSubmissionWriter` ("Fix B + Battle 2D"); `IdempotencyServiceTests` (11), `IdempotencyPersistenceIntegrationTests` (3) | End-to-end loop test is red (golden journey, TD-03) |
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
| **4C** | School ↔ child authorization | **NOT IMPLEMENTED** — design only | — | Battle 4C design report | PD-01 |

---

## Open findings register

| ID | Finding | Status | Evidence | Next dependency |
|---|---|---|---|---|
| **3R.1** | Remaining version consumers after 3R: challenge lifecycle (OD-09), adaptive version-completion (OD-10), lesson/assessment lifecycle audit (OD-08), localization/media ↔ version relationships, withdrawal semantics (PD-04) | OD / PD | 3R report §7, §14 | PD-04 before any Content Studio work |
| **L7** | Multi-skill activities: adaptive/review decisions use an arbitrary first skill of the activity | OD | Carried from 5A/5B reports (`deriveSkillContext`) | Battle 5D design |
| **L8** | Adaptive and Smart Review decisions run only if the client calls `/adaptive-next` and `/smart-review-check` after completion | OD | `ActivityAttemptController`; the frontend drives these calls | **Battle 5D** |
| **N2** | Smart Review item find-or-create is not serialized (race can duplicate review items) | OD | 5A reconnaissance; **not re-verified in this reconciliation** | Battle 5D |
| **L9** | MATCHING has no scoring or interaction; refused at publish | FP / PD | `QuestionService.validateCompleteness`; `AnswerScoringServiceTests.matchingIsNeverScored`; frontend renders none | Interaction architecture phase |
| **N7** | Teacher-authored content tagged to a system skill is placed into **every** family child's path for that skill | OD (tenancy / content governance) | Raised in Battle 5 reconnaissance; observed again at runtime in 3R journey B (a teacher activity landed in a new family learner's path) | PD-01 / PD-05 |
| **TD-03** | Golden learning journey test is stale: it answers 1 of 2 required questions, so completion is refused (ERROR) since 5A | TD + PD on test intent | `GoldenLearningJourneyIntegrationTests` | Decide intent, then rehabilitate (first step of 5D) |
| **TD-04** | PRINCIPAL registration test expects rejection; `AuthController` returns 202 pending approval | TD + PD-07 | `PublicRegistrationRoleSecurityTests.registrationRequestingPrincipalIsRejected` | PD-07 |
| **OD-01** | No rate limiting anywhere (refresh, login, register) | OD | No limiter in source or pom; lockout covers password guessing only | Security hardening battle |
| **PD-02** | Access tokens stay valid for up to 15 min after logout/revocation/disable (stateless JWT) | PD | 4D §13 | Product/security decision |
| **TD-05** | `account_sessions.replaced_by_session_id` has no index; family walks are one query per link | TD | 4D §13; migrations | Index migration (small) |
| **OD-12** | Production CORS / deployment topology undecided: no production frontend origin; compose `frontend` builds from nonexistent `../YulMe-Frontend`; no production frontend serving config; staging lacks `forward-headers-strategy` | OD / PD | N5 §2, §9 | Deployment decision |
| **TD-02** | `backend/.env.example` is stale (JWT_ISSUER shown optional, JWT_SECRET/JWT_REFRESH_TOKEN_TTL/DB_USERNAME/DB_PASSWORD missing, CORS default wording) | TD | N6 §9; **the file is blocked by the user's permission settings** | User edit or permission change |
| **TD-06** | Adaptive threshold/configuration drift: `AdaptiveEngine` hard-codes 0.4/0.8 and `MIN_EVIDENCE_*` (also in `SmartReviewService`, `ParentInsightService`), while `MASTERY_OBJECTIVE_THRESHOLD` is configurable and unlinked | TD / PD | Source constants | Battle 5D or a config follow-up |
| **PD-04** | Withdrawal / unsafe-content semantics: no way to pull a wrong or harmful version; RETIRED only means superseded, so a wrong answer key keeps being served wherever pinned | PD | 3R §7 #7, §13 | Product decision |
| **OD-08** | Lesson and assessment versions share the lifecycle; their learner consumers are not audited | OD (unaudited) | 3R §14 | 3R.1 |
| **OD-09** | Challenges pin one exact ActivityVersion; progress counts only that version, so it stalls once children move on to v2 | OD / PD | `Challenge.activityVersionId`, `ChallengeService.progress` | PD: count by activity or by version |
| **OD-10** | Adaptive treats "completed" per version id: a child who finished v1 can be recommended v2 of the same activity | OD / PD | `AdaptiveEngine.pickActivity` (`completedActivityVersionIds`) | Battle 5D design |
| **TD-01** | Workspace is not under version control; the only checkpoint is the Battle 4 zip | TD (high) | `git rev-parse` fails at the root and in `backend/` | User: initialise git and commit the baseline |
| **TD-07** | Resuming an existing attempt answers 201 Created instead of 200 | TD (minor) | 3R runtime journey C | Contract cleanup when attempt APIs are next touched |

---

## A. Architecture baseline

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Stack | VC | Spring Boot 4.1.1 / Java 21, PostgreSQL 18, Flyway (V1–V35), JPA `ddl-auto: validate`; Vite + React 18 + TypeScript + Tailwind frontend | `pom.xml`, `application.yaml`, `frontend/package.json` | — | — | — |
| Backend authority | VC | Scores, evidence, mastery, adaptive/review decisions, lifecycle and authorization are server-side | Code across `learning`, `learner`, `review`, `parent` | 2A–5C | L8: the server doesn't *trigger* post-completion decisions itself | 5D |
| Versioned, pinned content | VC | Immutable published versions; learner references pin exact ids (RESTRICT FKs) | 3R reconstruction | 3, 3R | 3R.1 | — |
| Observability | PV | Correlation-id filter + MDC in logs; actuator health/info; audit events | `CorrelationIdFilter`, `CorrelationIdFilterTests` (2), `AuditService` tests | — | No metrics/alerting/production log strategy | Release-readiness phase |
| Version control / checkpoints | TD | **Not a git repository**; one zip checkpoint (Battle 4) | `git rev-parse` fails at root and in `backend/` | — | All work since Battle 4 lives only in the working tree | **TD-01: initialise git and commit the current baseline** (user action/approval) |

## B. Identity / authentication

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Register / login / refresh / logout | VC | `/api/v1/auth/*`; PARENT self-registration; lockout on failed logins | `SessionReliabilityIntegrationTests`; N6 runtime (lockout after N attempts) | 4A, 4D, N6 | — | — |
| Session state machine | VC | Hashed single-use refresh tokens, rotation, replay → family revocation, logout, disabled account refused | 17 integration tests; runtime + browser (4D) | 4A, 4D | Hop-cap fix TESTED only | — |
| Frontend session contract | VC | 401 → one serialized refresh → one retry; 403 never refreshes; "unavailable" keeps the token | Browser verified (4D) | 4D | Browsers without Web Locks fall back to in-tab coalescing only | — |
| Access-token revocation latency | PD | ≤ 15 min after logout/disable/role change | 4D | 4D | PD-02 | — |
| Refresh token storage | TD | Refresh token in `localStorage` (XSS exposure) | `tokenStore.ts` | 4D | Hardening option: httpOnly cookie + CSRF design | Security hardening |
| Email / phone verification | FP | `Account.emailVerifiedAt` exists; no verification flow or endpoints | `AuthController` has only register/login/refresh/logout | — | Real tokens, expiry, resend limits (`CLAUDE.md` §11) | Identity phase |
| Password reset / change | FP | None | `AuthController` | — | Whole flow | Identity phase |
| PRINCIPAL self-registration | PD | 202 pending ADMIN approval; approve endpoint exists | `AuthController`, `AdminController`; TD-04 | — | Stale test contradicts code | PD-07 |
| Staff provisioning / bootstrap admin | PV | ADMIN creates TEACHER/PRINCIPAL; optional bootstrap admin via `platform.bootstrap-admin.*` | Exercised at runtime in 3R | — | No automated tests; bootstrap vars aren't in the N6 contract (documented as YAML only) | Admin phase |

## C. Authorization

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Family → child access | VC | `FamilyAccessGuard` on child-scoped endpoints | 4D test (stranger → 403); 3R runtime (other child's path item → 403) | 2A, 4D | — | — |
| Content write authorization | PV | `ContentAccessGuard`: owner, or ADMIN/PRINCIPAL; system content ADMIN-only | Code; positive runtime path (3R) | 4B | No test for the TEACHER-refused case | Test |
| School ↔ child authorization | PD | Class enrollment endpoint exists; the school-authorization relationship does not | Battle 4C design | 4C | **PD-01**: the school authorization model | Product decision, then implementation |
| Content catalog reads | PV (by design) | `GET /activity-versions/{id}` is not child-scoped: any authenticated user reads any learner-visible version | `ActivityContentController` javadoc | 3, 3R | Acceptable while content is shared catalog; revisit with school-private content (N7) | PD-05 |
| Teacher content reaching family children | OD | N7 | Runtime observation (3R) | — | Tenancy boundary for authored content | PD-01/PD-05 |

## D. Learning Path

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Path seeding | VC | Curriculum objectives → skills → current PUBLISHED activity versions | `EnrollmentService.seedInitialPath`; 3R tests (new learner gets v2) | 2B, 3R | N7 (unscoped teacher content) | PD-05 |
| Next activity / continuity | VC | First PENDING/IN_PROGRESS item; pinned versions stay playable after republish | 3R journeys A, C | 3R | — | — |
| Concurrent insertion | VC | Row lock on the path for every insertion | 5A tests | 5A | — | — |
| Auto-enrollment | PV | Only when exactly one curriculum matches | Code only | 2B | No automated test | Test |

## E. Activities / attempts

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Start / resume / answer / complete / abandon | VC | Server-scored; one IN_PROGRESS attempt per child+version; resume returns the same attempt | `ActivityAttemptServiceTests`; 3R journeys | 2A, 5A, 3R | Resume answers 201, not 200 (TD-07) | — |
| Answer contract | VC | MC = option id; canonical TF/NUMERIC; SHORT_ANSWER trimmed and case-insensitive; invalid → 422 | `AnswerScoringServiceTests` (7) | 5A | L9 | — |
| Required-question completion | VC | Completion refused until every item is answered | `completeAttemptRejectsAnAttemptWithMissingRequiredAnswers` | 5A | Golden test not updated (TD-03) | 5D |
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
| Recommendation | PV | REINFORCEMENT / CHALLENGE / CONTINUE from mastery + evidence gates; selects current PUBLISHED versions | `AdaptiveEngineTests` (1), cold-start tests, 3R test | 5A, 5B, 3R | L8 (client-triggered), L7, OD-10, TD-06 | **5D** |
| End-to-end loop coverage | TD | The golden journey (the only E2E loop test) is red | TD-03 | — | Rehabilitate | 5D |

## H. Smart Review

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Flag / schedule / insert / outcome | PV | Evidence-gated flagging; due review inserted under the path lock; current PUBLISHED versions only | Cold-start tests; 3R test and journey E | 2D, 5A, 3R | N2 race; L8 client trigger | **5D** |

## I. Progression

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Progress summary / streaks | PV | Completed activities, minutes, streak, path counts | `LearnerProgressController`, `StreakController` | — | No automated tests; no progression model beyond counts | Child/parent product phases |

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
| Learning Buddy | FP | Marketing sections only (`LearningBuddy.tsx`, `ChildrensWorld.tsx`) | Frontend source | — | Authored character system (`CLAUDE.md` §19) | Child product phase |

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
| Parent web app | PV | `ParentHome`, `AddChildForm`, `ChooseCurriculumPanel`, `ChildOverviewPage`; insights via API | Signed-in `/parent` browser-verified (4D, N5); full parent workflow **not** browser-verified | — | Coverage/history display (PD-06), goals, consent workflow, calm evidence-grounded dashboard | Parent product phase |
| Consent | PD / FP | `ConsentRecord`/`ConsentService` exist, **not wired** | `ChildService` javadoc | — | Real consent workflow | Product/legal decision |

## Q. Child product

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Play session | PV | `PlaySession.tsx`: next → start → content → answer → complete, inside the parent's session (no child account) | API journeys verified (3R); **no recent browser play-through** | 3R | Child-grade UX, Learning Buddy, motion, audio, accessibility pass (`CLAUDE.md` §18–19) | Child product phase |

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
| Build / lint | PV | `tsc -b` + `vite build` pass; lint 0 errors (1 pre-existing warning) | 4D | 4D | Not re-run after 4D (no frontend changes since) | — |
| Auth / session | VC | See B | Browser (4D) | 4D | — | — |
| Automated tests | TD | **None** — no test script or framework | `package.json` | — | Unit/component tests; E2E for critical flows | Testing phase |
| README accuracy | TD | Still says authenticated shells are placeholders; parent/learning/teacher pages now exist | `frontend/README.md` §Current status | — | Update | Next frontend battle |

## U. Testing / verification

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Backend suite | PV | 168 tests (unit + Testcontainers integration); 166 pass | Last run 2026-09-25 | all | TD-03, TD-04 red; 2B, 4B, challenges, school, progress untested | 5D (golden journey first) |
| CI | TD | **None** (no `.github` or other pipeline) | Repository | — | Every verification is manual | TD-01 then CI |
| E2E browser automation | FP | Browser verification is manual per battle | Battle reports | — | Automated critical-flow E2E | Testing phase |

## V. Security hardening

| Item | Status | Current implementation | Evidence | Battle | Remaining gap | Next dependency |
|---|---|---|---|---|---|---|
| Implemented | VC | Lockout, hashed rotating refresh tokens, replay detection, 401/403 semantics, exact-origin CORS, fail-loud config, draft-content isolation, family access guards | Battles 4A–N6, 3R | — | — | — |
| Open | OD / PD | No rate limiting (OD-01); revocation latency (PD-02); refresh token in `localStorage`; `replaced_by` index (TD-05); N7 tenancy; no identity verification; no password reset; 4B TEACHER-refused case untested | See registers | — | — | Security hardening battle |

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
| PD-05 | Whether teacher-authored content may enter family (non-school) curricula at all (N7) | Battle 5 recon; observed again in 3R |
| PD-06 | Parent UI display of coverage, evidence strength and insight history | 5C |
| PD-07 | PRINCIPAL self-registration: pending approval (code) vs rejected (stale test) | Earlier battle reports (stale test) |
| PD-08 | Versioned vs identity-level skill mappings | 3R |
| PD-09 | Subscription / entitlement business model | — |
| PD-10 | Unstarted path items on a superseded version: keep pinned (current) vs an explicit refresh rule | 3R |

---

# CURRENT VERIFIED PROJECT STATE

- **Backend foundation integrity baseline: established.** Accepted and evidenced: 2A, 2B
  (inspected only), 2C, 2D, 3, 4A, 4B (partial), 4.1, 5A, 5B, 5C, 4D, N5, N6, and 3R
  (L4/L5 core).
- **Highest evidence:** the session/auth flow and CORS are BROWSER VERIFIED; configuration
  and content lifecycle are RUNTIME VERIFIED; the learning-state battles (5A–5C) were
  RUNTIME VERIFIED at battle time and are TESTED now.
- **Test suite:** 168 run, 166 pass; the 2 failures are the known stale tests (TD-03, TD-04).
- **Not under version control;** the last checkpoint is Battle 4.
- **FULL BOOKEYOND KID PRODUCT: NOT COMPLETE.**

# REMAINING ENGINEERING GAPS

1. Post-completion loop is client-triggered and race-prone: **L8, N2, L7**, OD-10, TD-06.
2. End-to-end learning-loop test red (**TD-03**); PRINCIPAL test red (TD-04, needs PD-07).
3. Tenancy: teacher content in family paths (**N7**).
4. Version consumers beyond activities/questions (**3R.1**: OD-08, OD-09, localization/media).
5. Security hardening: **rate limiting (OD-01)**, `replaced_by_session_id` index (TD-05),
   token storage, identity verification, password reset, 4B negative test.
6. Deployment: production origin/topology (OD-12), compose frontend context, staging
   forwarded headers, stale `.env.example` (TD-02).
7. Engineering infrastructure: **no git (TD-01)**, no CI, no frontend tests, no automated E2E;
   untested areas (2B auto-enrollment, school, challenges, progress/streaks).
8. Dead or unlinked state: `confidence` never read; hard-coded thresholds.

# PRODUCT GAPS

Child experience (UX, Learning Buddy, motion, audio, accessibility), parent dashboard depth
(coverage/history, goals), consent workflow, school domain (PD-01) and principal/teacher
consoles, admin console and Content Studio, interaction types (MATCHING and child-native
interactions), identity verification and password flows, subscription/entitlements, and
the open product decisions PD-01 to PD-10.

# FUTURE PHASES

1. **Learning-loop completion:** 5D (L8 + N2 + golden journey), then L7 and adaptive semantics.
2. **Security hardening:** rate limiting, revocation strategy, token storage, identity
   verification, password reset.
3. **Content platform:** 3R.1, withdrawal semantics, Content Studio, localization/media.
4. **Child product,** then **parent product** depth.
5. **School domain** after PD-01.
6. **Release readiness:** git, CI, frontend and E2E tests, deployment topology, observability.
7. **Subscription/entitlements** after PD-09.

# IMMEDIATE NEXT BATTLE

**Prerequisite (user action):** put the workspace under version control and commit this
baseline (TD-01). This takes minutes and protects every later battle.

**Battle 5D — Server-authoritative post-completion (L8 + N2), starting with the golden journey.**
1. Decide what `GoldenLearningJourneyIntegrationTests` is meant to prove and rehabilitate
   it, so the whole loop has one end-to-end regression check.
2. Make adaptive and review decisions happen server-side when an attempt completes (L8).
3. Serialize Smart Review find-or-create (N2).

Why now: content can no longer strand a child (3R), sessions and configuration are
trustworthy (4D/N5/N6), and the post-completion loop is the largest remaining
learning-integrity risk. L7 and OD-10 feed into 5D's design but should stay separate
unless they prove inseparable.
