# Bookeyond Kid — Product improvement roadmap (post-testing program, from 2026-10-22)

The working record for the program that follows coworker testing. It holds the research, the design of each
improvement, the order of work, and the **current state**. If work is interrupted, resume at the first milestone
below that is not marked done; never redo a done one without a concrete reason.

Labels used throughout: **[V]** verified behaviour of an existing product (with source), **[R]** finding supported by
research, **[P]** our proposed design decision, **[Q]** open question needing the owner.

---

## Current state

| Milestone | Status | Commit(s) | Migrations |
|---|---|---|---|
| M1 Research, inventory and roadmap | **done** (this document) | see git log | — |
| M2 Landing page, ages 3–12, public demo | **done** | see log | V63 |
| M3 Child wellbeing: daily limits and graceful stops | **done** | see log | V64 |
| M4 Self-service school registration and paid onboarding | **done** | see log | — |
| M5 Authoring UX, direct upload, content visibility | M5a done (upload in the form); M5b done (school sharing by copy, D-13); PUBLIC deferred (D-14) | | |
| M6 Feature-specific paywall messages | **done** | see log | — |
| M7 Pagination and information density | **done** | see log | — |
| M8 Payment QR, manual reconciliation, secure grants | **done** (M8a step-up and reconciliation, M8b instructions and KHQR) | see log | V67 |
| M9 Phone registration and verification foundations | M9a done (administrator authenticator app); M9b-1 done (a verified phone on an account, V71); sign-in and password reset by verified phone done (V72); email stays required (D-15) | | |
| M10 Child access across devices, trusted caregivers | **done** (M10a child devices, M10b caregivers) | see log | V65, V66 |
| M11 Storage-provider abstraction, school storage choice | planned | | |
| M12 School management: foundation and first modules | **P1 done** 2026-10-28 (academic years and terms, V77); discovery and decisions S1-S6 in `docs/SCHOOL-MANAGEMENT.md`; P2+ **needs the owner first**: linking a school's student record to a family's child (consent, Battle 4C "school <-> child authorization" is not designed) and whether school administration (attendance, exams, report cards) is in scope now - CLAUDE.md keeps children and parents first and warns against premature school-ERP | | |
| M13 Integration, role verification, docs, staging readiness | **engineering done** 2026-11-04: role journeys (row below); readiness docs and staging checks brought up to date 2026-11-03 (`DEPLOYMENT.md`, `docs/DEPLOY-VERCEL-RAILWAY.md`, `docs/SECURITY-AND-RELIABILITY.md`); a phone session over a slow connection measured; the staging deployment itself waits on the owner's hosting accounts (EX) | see log | |
| L1 Learning experience: "Learn first" activity introductions (owner brief 2026-10-25) | **done** | see log | V73 |
| L2-L11 Learning experience follow-ups (explanation tip, D-16 hints, recorder, D-17 two days, phone layouts, Studio gaps/guide, parent fixes and help) | **done** | see log | V74 |
| M13 Integration, role journeys | role journeys on phone (360 px) EN/KM for all five roles **done** 2026-10-27; readiness docs done 2026-11-03 | | |
| F1a Landing page: every word editable without code | **done** 2026-10-27 | see log | V75 |
| F1b Public demo journey for families: a child tries a real activity (buddy, introduction, questions, feedback, completion) then "Create a parent account" or "Not now"; what an account enables | **done** 2026-10-27 (teach-first, account explanation, Not now, demo picker shows replaced versions) | see log | - |
| F1c Parent demo with representative sample data (progress, evidence, how to help) | **done** 2026-10-27: `/try/parent`, a fictional sample child labelled on the page, static, nothing fetched or stored | see log | V76 |
| F1d Landing page redesign: concise copy, purposeful Learning Buddy/character motion showing the real app | **F1d-1 done** 2026-10-27: "How it works" plays the learning loop (child answer -> evidence -> standing -> next step) with the real components; **F1d-2**: closing headline and footer line editable, parent demo linked from "For parents"; **F1d-3**: "For parents" says what the product actually does; **F1d-4**: the buddies can be chosen on the page, as a child does; **F1d-6**: shorter on phones (redundant "For families / For schools" removed); **F1d-7** 2026-11-03: schools' hardest-questions and spreadsheet-paste capabilities, parents' missed questions with why | see log | |
| F2 Legal and launch readiness: Terms, Privacy, Cookies (if used), children's data and parental consent, deletion, media, third parties | **F2a done** 2026-10-27: `docs/legal/DATA-INVENTORY.md` (facts from the code + 10 questions for a lawyer); **F2b drafts done**: `PRIVACY-NOTICE-DRAFT.md`, `TERMS-DRAFT.md` (unpublished, owner/lawyer placeholders); next: owner facts + qualified review - written from what the product actually collects and does; jurisdiction questions (Cambodia PDP draft, D-11) for qualified review; no claims of compliance without it | | |

**Execution order (revised 2026-10-23, owner's continuous-execution brief: parent and child first, school management
deferred until the family experience is coherent):** M6 → M7 → M10 → M8 → M9 → M5 → M11 → M13 → M12. M12 waits
until M6, M7, M10 and M8 are done; M13's integration pass runs before it and again at the end.

### Blueprint review (2026-10-23)
- **Dependencies**:
  - M8's grants and payment-method changes need a re-authentication step, so M8 adds password step-up;
  - M9's TOTP then strengthens the same gate;
  - M10's link by SMS and phone caregivers depend on M9; its 6-character code and email paths don't, so M10 doesn't
    wait for M9;
  - M6's `PURCHASE_UNAVAILABLE` relies on M4's self-service school plans (done).
- **External blockers** (no milestone waits on them; each degrades honestly):
  - D-04 (SMS provider): the phone flow runs only with the local test sender;
  - D-07 (genuine KHQR): no QR is shown;
  - D-06 (DP Cloud): S3-compatible storage only.
- **Simplifications**:
  - M7 pages only lists that can grow, with no notification system;
  - M11 starts with the provider interface and S3-compatible storage, and per-school storage choice follows D-06;
  - M12 is staged as foundation, then modules.
- **Security gaps carried as risks**:
  - self-service principals (M4) let anyone create a principal account and an unpaid school. The register rate limit
    applies, and an unpaid school can do nothing (402), so it is not spam-amplifying, but administrators should see
    new schools (audited as `SCHOOL_CREATED`);
  - the M10 child-scoped session is the largest new authorization surface. It gets its own endpoint-sweep test, like
    the cross-family sweep.
- **No contradictions found** between milestone designs. One correction: M4's "school name at registration" is
  dropped, because the server had nowhere to keep it and the form was silently discarding it. The name is asked for
  after sign-in.

Baseline at the start: `master` at `db4b6c8`, CI green, V62. The owner's uncommitted edit to
`AssessmentAnswerSubmissionWriter.java` is preserved and never staged. The coworker preview (`D:\bk-work\preview`) keeps
its data; its stack is stopped (the machine restarted) and is not reset by this program.

---

## 1. What exists (inventory, from the code on 2026-10-22)

| Area | Where it lives today | Relevant facts |
|---|---|---|
| Landing | `features/marketing/{LandingPage,messages,trust.messages,Places}.tsx` | Hero title shown twice: in the UI language as `h1` and in the other language above it (with its `.`/`។`). Age copy "4 to 9" in 3 places (+ sign-in footer). No editable content source. |
| Ages | `child/entity/Child` (`ageOn`), `age_hubs` (V10, admin data: `min_age`, `max_age`) | No server rule depends on 4–9. Age hubs decide which subjects a child is offered. |
| Wellbeing | Break reminders (Y36, parent-set, between activities only), grown-up gate, no streak pressure, no points | No daily limit. Sessions end at natural points. |
| School onboarding | Principal applies at `/register` → `PENDING_APPROVAL` → admin approves → principal creates school → admin invoices → payment confirmed (Y44) | School plans can't be self-service (server rule, Y35). |
| Authoring | Content Studio (subjects → skills → objectives → questions → activities → curricula), activity builder (Y42), `MediaSlot` (upload in the question form), media library (X13) | Ownership: platform (no owner) or one author (`owner_account_id`). No school-wide sharing. |
| Entitlements | `EntitlementService` (family), `SchoolEntitlementService` (school, paid rule), `PlanLimitException` (409 `entitlement`), `SchoolPaymentRequiredException` (402) | Server enforces. Frontend: `PlanLimitNote`, school paused panel. |
| Lists | `PageResponse` used by admin lists; `AccountController.devices` returns all sessions unpaginated | No notification system exists. |
| Payments | `payment/**` (V62): orders, ledger, manual transfer (env instructions), ABA PayWay prepared, simulator (refused in prod) | Admin confirms with receipt reference; refunds; school invoices. |
| Identity | Email + password, email verification, recovery, sessions with rotation, lockout, rate limits | No phone, no second factor. |
| Child access | A child plays inside the parent's signed-in session (`/learn/:childId`), grown-up gate to leave | No per-child device link; no caregiver role. |
| Storage | `MediaStorage` (local disk, SHA-256 keys), `StorageService` (per-owner allowances), signed URLs | One provider, no abstraction. |
| School management | Schools, classes, teachers, join codes, family-granted access, class insights, assignments | No school years, attendance, exams, grades or report cards. Children exist only through families. |

---

## 2. Research (2026-10-22, plus the 2026-10-18 brief in PRODUCT-MODEL-BRIEF.md, which is reused, not repeated)

### 2.1 Children, motivation and wellbeing
- **[R]** Self-determination theory: motivation lasts when activities support **competence** (challenge matched to
  skill, informational feedback), **autonomy** (meaningful choice) and **relatedness**. Controlling, expected rewards can
  undermine intrinsic motivation. ([SDT: intrinsic motivation](https://selfdeterminationtheory.org/topics/application-intrinsic-motivation/);
  [ACM Interactions 2024, "Cultivating intrinsic motivation in children"](https://interactions.acm.org/archive/view/september-october-2024/cultivating-intrinsic-motivation-in-children-setting-goals-for-interaction-design))
- **[R]** Feedback framing matters (negative-feedback meta-analysis, Fong et al. 2019, cited by SDT). Informational
  feedback ("the right answer is 8, because…") supports competence; shame or loss framing does not.
- **[R]** Screen time: WHO 2019 guidance for under-5s is at most 1 hour of sedentary screen time a day; AAP recommends a
  family media plan. **No Cambodian law sets a screen-time limit** (2026-10-18 brief). Neither is a product rule; both
  inform parent-set defaults.
- **[R]** MoEYS Instruction No. 50 (2023) keeps non-learning devices out of classrooms (binding in public schools).
- **[P] Product principles** (applied everywhere, not a feature):
  1. Active over passive: every screen asks the child to do, choose or make something; there is no autoplay and no
     endless feed.
  2. Honest progress: practice, improvement and mastery stay distinct, and the garden grows only from evidence.
  3. No manipulation: no leaderboards, child-to-child comparison, currencies, loot mechanics, loss-aversion streaks
     or engagement notifications.
  4. Graceful boundaries: a limit never removes a question mid-answer. Stopping happens at the end of an activity,
     announced in advance, with a calm close and a clear way back tomorrow.
  5. Age range 3–12 means different readers: pictures and audio carry instructions for pre-readers; text stays short
     for everyone.
  6. Parents decide limits; the product suggests, never diagnoses. No health claims (we do not claim to treat or prevent
     "brain rot").

### 2.2 Exploring before paying
- **[V]** Khan Academy Kids is free; ABCmouse sells a membership; Seesaw's free Starter limits classes, not content
  (2026-10-18 brief).
- **[P]** A **public demo**: a few sample activities, chosen by an administrator from published content, playable
  without an account. Answers are checked by the server but never stored, there is no evidence or mastery, and it is
  rate-limited. Registration is needed for anything saved; payment for plan-limited breadth. **No free trial is
  promised** until the owner sets one ([Q] D-02).

### 2.3 Payments in Cambodia
- **[R]** Bakong KHQR: a **static** QR has no amount and allows unlimited payments to an account. A **dynamic** QR
  carries the amount and an expiry, and its payment can be checked through Bakong's API by the MD5 of the QR payload.
  The check needs a registered developer token. Official SDK document:
  https://bakong.nbc.gov.kh/download/KHQR/integration/KHQR%20SDK%20Document.pdf; hosts `api-bakong.nbc.gov.kh`,
  sandbox `sit-api-bakong.nbc.gov.kh`. The details are from unofficial wrappers
  ([bakong-khqr on PyPI](https://pypi.org/project/bakong-khqr); [Go wrapper](https://pkg.go.dev/github.com/chhunneng/bakong-khqr));
  the official PDF wasn't readable here.
- **[P]** An uploaded static QR image is **instructions, never proof**. Orders stay PENDING until an administrator
  reconciles them against a genuine receipt (existing manual flow). Automatic confirmation needs a provider that
  confirms (ABA PayWay check-transaction, or a Bakong dynamic-QR check with an NBC token), each behind the existing
  `PaymentProvider` abstraction and only marked "integrated" after a real sandbox test.

### 2.4 Identity, phone numbers, family access
- **[R]** NIST SP 800-63B-4 classifies SMS/PSTN one-time codes as **restricted**: usable only with an unrestricted
  alternative offered, users told of the risks, and controls against SIM swap and interception. Rate limiting stops
  guessing, not phishing. ([TypingDNA summary](https://blog.typingdna.com/nist-sp-800-63b-rev-4-sms-otp-is-now-a-restricted-authenticator-but-we-have-the-fix/);
  primary: pages.nist.gov/800-63-4)
- **[P]** A phone number is an **identifier and a recovery/verification channel**, verified by a one-time code. It does
  not make an account "more secure". Sign-in still needs the password. SMS is never the only factor for administrators.
- **[V]** Khan Academy links a second adult as a "coach", not a second parent
  ([support thread](https://support.khanacademy.org/hc/ka/community/posts/4408355398285-Add-a-Parent)). Products keep
  "another adult" and "this child on another device" as separate permissions.
- **[P]** Two distinct grants, never conflated:
  - **child device link**: one child's learning space on another device, nothing else;
  - **caregiver**: another adult with a chosen scope over chosen children, never billing, security, export or deletion.

### 2.5 School management (SIS)
- **[V]** The Ministry's own **SIS Cambodia** app covers teacher attendance-taking, score entry and report cards
  ([App Store](https://apps.apple.com/app/id6478900005)).
- **[R]** SALA (SalaTech) is a Cambodian cloud school-management system from K-12 to university, with 6 modules and
  more than 20 features, and an LMS feeding automated scores ([VC4A profile](https://vc4a.com/ventures/sala-tech-digital-solutions-cambodia-co-ltd)).
  Its module details are not public; not verified.
- **[P]** Standard modules, in dependency order: academic years and terms → school student records and enrolment →
  attendance → assessments and score entry → configurable grading → report cards and publication to families →
  promotion and transfer → timetables. Learning-platform evidence and mastery stay a **separate kind of evidence**,
  never mixed into grades.

### 2.6 Storage
- **[R]** Daun Penh Data Centre (DP Cloud) sells colocation, VPS, cloud servers and hosting. **No public object-storage
  API was found** ([b2b-cambodia profile](https://b2b-cambodia.com/cambodia-business-directory/daun-penh-data-centre-daun-penh-cloud);
  contact dpdatacenter.com).
- **[P]** A `StorageProvider` interface with the current local-disk provider, then **S3-compatible** object storage
  (most providers expose it). DP Cloud is supported only if it offers an S3-compatible endpoint, confirmed in a real test
  ([Q] D-06).

---

## 3. The improvements, designed together

Dependencies:
- Paywall messages (M6) build on the entitlement problems M4 introduces for schools.
- Payment QR and grants (M8) extend the payment lifecycle M4 uses for school checkout.
- Phone identity (M9) comes before invitations by SMS (M10).
- The storage abstraction (M11) comes before school-chosen storage.
- Content visibility (M5) uses the school relationships the SIS (M12) also needs.
- Ages 3–12 (M2) and wellbeing (M3) shape the demo and the child experience.

### M2 — Landing page, ages 3–12, public demo
- **Problem**:
  - the hero is cramped on phones;
  - the quotation carries sentence punctuation;
  - the copy says ages 4–9;
  - prospects can't feel the product before registering.
- **Design**:
  - Quotation: the other-language line becomes a typographic quotation with no trailing `.`/`។` (stripped only for
    display).
  - Brand statements: a small `site_statements` table (text EN/KM, attribution type BRAND or TESTIMONIAL with a source
    note, order, visible). Administrators manage it in Operations. Only BRAND statements are seeded. A TESTIMONIAL
    requires a named source and the administrator's confirmation that it is genuine, and is audited.
  - Hero: phone first. The text comes first, then the painted scene as a 4:3 crop centred on the bridge and buddy. Tablet
    stacks at 16:10; desktop keeps the side-by-side composition. Checked at 375/768/1440 in both languages.
  - Ages: copy 3–12 everywhere. No curriculum or safety rule changes (none depends on it); content for ages 3 and 10–12
    is an owner/content decision ([Q] D-09).
  - Demo: an admin flag on a **published** activity ("public demo", at most 3). `GET /api/v1/demo/activities` returns
    learner-safe items. `POST /api/v1/demo/check` checks an answer for those items only and stores nothing; it is rate
    limited per client. The landing page offers "Try a lesson" → `/try`, which plays like the child space with a banner
    ("nothing is saved"), then sign-up. Isolation: no account, no attempt, no evidence row, no media outside the demo
    items.
- **Acceptance**:
  - the quotation shows no trailing punctuation;
  - the hero is composed at 3 widths in both languages;
  - administrators can add, edit, order and hide statements, and a parent can't;
  - the demo plays only flagged items, writes nothing (row counts unchanged), and is refused for unflagged items;
  - "3–12" appears in both languages.

### M3 — Child wellbeing: daily limits and graceful stops
- **Problem**: parents want boundaries without fights. The product must not end a child's work abruptly.
- **Design**:
  - The parent can set an optional **daily learning time** per child (off by default).
  - The server totals today's active minutes from attempts in the family's time zone.
  - When the limit is near, the child is told before the next activity starts ("one more, then rest"). When it is
    reached, the current activity always finishes, then a calm close screen appears ("Great work today. See you
    tomorrow"), with the buddy resting.
  - Continuing needs the grown-up gate, which grants extra minutes for today, audited for the parent.
  - The limit never interrupts a question; evidence is never lost; resuming works tomorrow.
  - A gentle suggestion is shown for children under 6, but the default stays off ([Q] D-08, PD-33).
- **Acceptance**:
  - minutes are computed server-side;
  - a limit reached mid-activity doesn't interrupt it;
  - the next start is refused with a clear reason, and the gate extends it;
  - another family's child is unaffected;
  - Khmer and English.

### M4 — Self-service school registration and paid onboarding
- **Lifecycle**: register (person + school name) → verify email → principal account ACTIVE (no admin approval) → school
  created in `AWAITING_PAYMENT` → plan selection (self-service SCHOOL plans with a price set by an administrator) →
  payment order (manual transfer with configured instructions; ABA when configured) → PENDING → trusted confirmation
  (administrator reconciliation or provider check) → school ACTIVE with the plan's limits → setup checklist (classes,
  teachers, curricula, codes).
- **Server rules**:
  - the Y35 "school plans can't be self-service" rule is **replaced** by "SCHOOL plans may be self-service when
    priced";
  - the Y44 paid-access rule is unchanged;
  - one pending order per school (existing unique index) and one school per registration (idempotency key);
  - a duplicate school name for the same verified principal returns the existing school;
  - a failed, expired, cancelled or simulated (prod) order never activates.
- **Unverified principals**: a principal must confirm their email before creating a school.
- **Existing principals** keep working. Existing PENDING_APPROVAL applications are approved automatically on email
  verification; the admin approval screen remains as a fallback.
- **Principal home**: setup progress, plan status, capacity used/limit, payment status and the next action.
- **Acceptance**:
  - an end-to-end school registration reaches ACTIVE only after confirmation;
  - pending, failed or simulated payments in prod grant nothing;
  - limits apply;
  - concurrent "pay" clicks create one order;
  - duplicate registration is safe;
  - families' own learning is unaffected.
- **[Q] D-01**: school plan prices and which school plans are self-service (administrators set them in Plans; nothing
  is invented).

### M5 — Authoring UX, direct upload, content visibility
- **Direct upload**:
  - `MediaSlot` gets file browse and drag-and-drop on every authoring form (question prompt, options, explanation
    image).
  - It shows type, size and dimensions, upload progress (XHR), errors, a preview, and replace or remove.
  - It uses the same `POST /studio/media` (same ownership, quota, dedup and inspection).
  - A failed upload never discards the draft's other edits.
- **Authoring flow**: the builder shows where the item sits (subject › lesson › activity), what is required versus
  optional, who will see it, and what publishing does. Progressive disclosure is kept.
- **Visibility** (new column `visibility` on lessons, the unit of ownership for activities, and on curricula):
  - `PRIVATE`: the author only, as now (the default);
  - `SCHOOL`: teachers of the author's schools can use it in their curricula and assign it;
  - `PUBLIC`: offered to all teachers and administrators as reusable content, after platform review when review is
    required (PD-20).
- **Enforcement**: `ContentOwnership.usableBy` becomes a server-side check of visibility × relationship, applied to
  discovery, use and assignment. Media follows the content that uses it (signed URLs are issued only inside readable
  content). Narrowing visibility never breaks existing curricula or learner evidence (pinned versions keep working).
  Copies keep their original author.
- **Acceptance**:
  - upload works inside the form;
  - quota and type rules still apply;
  - SCHOOL content is usable by a co-teacher and refused to a teacher at another school (API, not just lists);
  - PUBLIC requires review where configured;
  - narrowing keeps existing paths.

### M6 — Feature-specific paywall messages
- **Design**:
  - Every entitlement refusal carries a machine-readable reason: `PLAN_LIMIT` (which limit, used/limit),
    `SCHOOL_UNPAID`, `SUBSCRIPTION_EXPIRED`, `FEATURE_NOT_IN_PLAN` and `PURCHASE_UNAVAILABLE`.
  - The response lists **actual** plans that unlock it (from the plan catalogue, with price only if set) and what
    happens to existing data ("your children's progress stays").
  - One `EntitlementNotice` component shows it in context with the right action (upgrade, renew, billing, contact)
    in EN/KM.
  - Family and school reasons never mix.
- **Acceptance**: each reason renders correctly; no invented plan or price; the server still refuses.

### M7 — Pagination and information density
- Devices and sessions: server paging (`page`, `size` ≤ 20, default 6); the current device is pinned first; "Sign
  out other devices" stays. Paging respects ownership.
- Inventory of other long lists, each paged server-side where it can grow:
  - the payments ledger on an order;
  - a family's orders;
  - a class's children;
  - the teacher's assignment history;
  - an account's audit trail in admin.
  Security history is never truncated without a way to see more.
- Notifications don't exist. In-app notices (setup, payment status) are built as a paged list only when a real producer
  needs them (M4/M8).

### M8 — Payment QR, manual reconciliation, secure grants
- **Payment methods**:
  - The administrator configures a manual method's instructions (EN/KM) and an optional **genuine KHQR image** (an
    asset owned by the platform), replacing the environment variable.
  - The configuration is versioned and audited, and needs reauthentication.
  - Customers see the exact order amount, currency and reference beside the QR.
- **Customer evidence**: an optional payment reference and screenshot upload on the order ("evidence for review"). It
  never changes the state.
- **Reconciliation**:
  - The existing administrator confirmation is extended.
  - The receipt reference must be unique per method; a duplicate is refused.
  - The amount and currency are matched against the order's snapshot.
  - Reject with a reason allows a retry (new order).
  - Every action is audited and needs a recent reauthentication.
- **Grants**:
  - A separate `entitlement_grants` record (scope family, entitlement or plan, reason, start/expiry, granting admin,
    second admin optional), never a fake payment.
  - It requires reauthentication within 5 minutes.
  - **School grants stay refused** (Y44) unless the owner approves an exception ([Q] D-03).
  - TOTP second factor for administrators is added in M9 (the supported second factor); grants then require it.
- **Acceptance**:
  - a QR image alone grants nothing;
  - a duplicate receipt is refused;
  - an amount mismatch is refused;
  - a replayed confirmation is idempotent;
  - a grant needs reauthentication and leaves an audit record;
  - no real payment runs locally.

### M9 — Phone registration and verification foundations
- **Model**:
  - `accounts.phone_e164` (unique when verified) and `phone_verified_at`.
  - Normalization to E.164 with country rules (Cambodia +855 first-class, any country allowed).
- **Flows**:
  - register with email, phone, or both (at least one; a password always);
  - verify the phone by a 6-digit code (10-minute expiry, 5 attempts, 60-second resend cooldown, hourly cap,
    per-number and per-client limits);
  - sign in by email or phone;
  - recover by either verified channel;
  - change the phone with reauthentication and verification of the new number.
  - Responses never reveal whether an identifier exists.
- **SMS**: an `SmsSender` interface. A `LogOnlySmsSender` writes to the local mail catcher for tests and **refuses to
  start in prod**. No provider account is created and no message is sent ([Q] D-04).
- **Administrator second factor**: TOTP (RFC 6238), enrolled with a QR code in the app and required for grants and
  payment-method changes.
- **Acceptance**:
  - normalization;
  - code expiry, single use and attempt limits;
  - no enumeration;
  - email flows unchanged;
  - the test sender can't run in prod;
  - no phone number or code appears in logs.

### M10 — Child access across devices, trusted caregivers
- **Child device link**:
  - From a child's page the parent creates a link for **that child only**, by email or SMS when configured, or as a
    6-character code to type on the other device.
  - The token is single-use, high-entropy and expires in 15 minutes; it is stored as a hash, and the URL holds only a
    code exchanged by POST.
  - Opening it on the other device asks the parent to confirm on their own device (pending → approved), then gives that
    device a **child-scoped session**: it can use only that child's learner endpoints, never parent, billing or sibling
    endpoints.
  - The session is revocable from the parent's device list, with a limit of active child devices per child
    ([Q] D-05, proposed 3).
  - Progress stays server-authoritative. Concurrent devices share the same open attempt (the existing resume rule);
    answers are idempotent by item; a conflict tells the child "this activity continues on the other device".
- **Caregiver**: an invited adult (email; phone later) with a scope per child, either "see progress" or "open the
  child space". Never billing, security, export, deletion or adding children. Expiry, revocation and audit.
- **Acceptance**:
  - the child session is refused on parent and sibling endpoints;
  - expired, used or forwarded codes fail without revealing anything;
  - revocation is immediate;
  - concurrent answers produce no duplicates.

### M11 — Storage-provider abstraction and school storage choice
- **Interface** `StorageProvider` (put, get, exists/verify, delete, signed read):
  - `LocalDiskProvider` (today's behaviour);
  - `S3CompatibleProvider` (AWS Signature V4, path-style option), tested against a local S3-compatible server.
- **Per school**: `school_storage` (mode HOSTED or OWN, provider, bucket, endpoint, region, encrypted credentials, health,
  verified ownership marker).
  - Credentials are encrypted with AES-GCM using a key from the environment (`STORAGE_CREDENTIALS_KEY`); they are never
    returned to the frontend.
  - Endpoints are validated against SSRF: https only, no private or loopback addresses after DNS resolution, an
    allow-listed port.
  - A connection test writes, reads and deletes a marker object.
- **Switching**: new uploads go to the new provider. Existing files stay readable from the old one, or move through a
  copy job with verification. Nothing becomes unreachable.
- **Quotas**: hosted storage counts against the plan; own storage doesn't use Bookeyond's allowance (backups are the
  school's responsibility, and it says so).
- **[Q] D-06**: whether schools may bring their own storage, who pays for hosted storage, and DP Cloud's actual API.

### M12 — School management: foundation, then modules
- **Data ownership**:
  - **School student records** are the school's (name, student number, class), separate from a family's `Child`.
  - A record can be linked to a family's child only with the family's consent (the existing access model).
  - Teachers see only classes they're assigned to; principals see their school; administrators have audited access.
- **Foundation**: academic years and terms (one current), student records and class enrolment by term,
  subject-teaching assignments.
- **Module 1**:
  - attendance by class and day (present, absent, late, excused), corrections with reason and audit, monthly summary;
  - assessments: an exam or assignment per subject and term, maximum score, score entry, missing or exempt.
- **Module 2**:
  - grading schemes (weights per assessment type, rounding, letter or band thresholds), reproducible term totals;
  - report cards (draft → published to linked families);
  - promotion, transfer and withdrawal with history.
- **Rule**: attendance, exam scores and learning-platform mastery are different evidence. They are never combined
  automatically.

### M13 — Integration and readiness
- Role journeys (child, parent, teacher, principal, admin) in EN/KM on desktop and phone.
- Updates to the readiness page, DEPLOYMENT and the progress record.
- Tests: focused tests per milestone; the full suite at milestone boundaries; CI on every push.

---

## 4. Decisions that need the owner (nothing here is invented in the product)

| # | Decision | Until decided |
|---|---|---|
| D-01 | School plan prices; which school plans are self-service | Admins can set them; the registration flow shows "purchase isn't available yet" without a priced self-service plan |
| D-02 | A free trial (length, what it includes) | No trial; the public demo plus a free registration |
| D-03 | Any complimentary or unpaid school access | Refused (Y44) |
| D-04 | SMS provider and its cost | Test sender only (local); phone verification can't run in prod |
| D-05 | Limit of child devices / caregivers per family | Proposed 3 child devices per child, 2 caregivers per family; editable as plan entitlements |
| D-06 | Bring-your-own storage offering; who pays for hosted storage; DP Cloud API | Abstraction + local + S3-compatible built and tested; no DP Cloud claim |
| D-07 | Genuine KHQR image and payment instructions (your account) | Manual method shows "not configured"; no QR is invented |
| D-08 | Default daily limit or break reminder for young children | Off by default, suggested in the add-a-child flow |
| D-09 | Content for ages 3 and 10–12 | Copy says 3–12; subjects follow the age hubs the admins configure. **Owner, 2026-10-27: the platform audience is deliberately 3–12 - never narrowed to the current content; public copy states the audience and what is available separately** (BRD section 2) |
| D-10 | Genuine testimonials (with permission) | Only brand statements |
| D-11 | PDP law status, child-safety page, consent for school student records (PD-31, PD-32) | Minimal data; school records stay school-scoped |
| D-12 | New third-party libraries (e.g. libphonenumber, an S3 client) and container images (S3-compatible test server) | Asked at the milestone that needs them. **libphonenumber approved 2026-10-24** (M9b; pinned 9.0.38, two months old when added) |
| D-13 | How a co-teacher uses a lesson shared with their school (decided 2026-10-23) | **Copy to own**: the colleague copies it into their own content (drafts, crediting the original); children's paths still draw only on their curriculum owner's and platform content |
| D-14 | PUBLIC content for every teacher (decided 2026-10-23) | **Deferred** until it is decided who reviews teachers' content before the whole platform sees it |
| D-15 | Phone-only accounts; resetting a password by text (decided 2026-10-25) | **Email stays required** on every account; a code texted to the verified phone can also reset a forgotten password |
| D-16 | Does a right answer given after opening the hint count as evidence the child "showed it on their own"? (decided 2026-10-27) | **Record it; count as practice.** Each answer records whether the hint was opened (V74); a hinted answer, right or wrong, is practice: scored and shown, but it moves no mastery and does not count as a remembered review. Answers from before V74 stay "not recorded" |
| D-17 | Can a skill read "Secure" after one sitting? (decided 2026-10-27) | **Two different days.** "Secure" (skill), a secure objective and the "has mastered" insight all need the evidence to span at least two learning days (the family's day zone); before that a skill at the mastery level reads "Progressing" and the parent is told it is waiting for another day. Mastery numbers and every decision made from them are unchanged |
| D-18 | Should something bring a skill back on another day so it can be confirmed? (proposed 2026-10-27) | **Open, recommended.** After D-17 a skill can wait for "another day" with nothing on the path practising it (Smart Review only reacts to low mastery). Proposal: a light "check it again" review item for a skill at the mastery level whose evidence is from one day, due the next learning day, shown to parents as such. Changes what children are given - owner's call |
| D-19 | Who writes the real curriculum, and in which languages? (raised 2026-10-27) | **Open - not a code task.** Every installation starts from the V29 developer seed: one English "Maths - Junior" (ages 4-9) of 6 activities x 2 questions, all bare symbolic sums ("3 + 4 = ?") typed on a keypad - no pictures, recordings, explanations, hints or introductions. For 4-6 year olds (many not reading) that is symbols without concrete grounding. The Studio can now carry everything a good item needs (picture choices, counting images, recordings made in the browser, introductions, hints, explanations, a per-activity gap line); what is missing is educator-authored content, a Khmer edition, and native review (PD-14). Needs the owner: who authors (educators/partners), first scope (e.g. counting and addition to 10 with pictures and voice, EN + KM), and review before families see it |
| D-20 | After a wrong answer, should a child get a second try before the answer is shown? (raised 2026-10-29) | **Open.** Today: one try, then the right answer, the author's explanation - or, without one, the author's hint as "a way to think about it" (2026-10-29) - and Smart Review brings the skill back later. Options: (a) keep one try; (b) a second try for wrong answers, the first answer recorded as evidence and the second as practice (as D-16 does for hinted answers); (c) a second try only for the youngest age hubs. Recommendation: (b) for typed answers (numbers, words) and choice questions with three or more choices - with only two choices, or true/false, a second try can't be wrong, so it teaches nothing; it changes the play flow and the parent's view of attempts, so it waits for the owner. |

---

## 5. Progress log
- 2026-10-22: M1 done. Inventory, research and roadmap written; next M2.
- 2026-10-22: **M2 done.**
  - **Hero**: composed per width. On phones and tablets the words sit on paper, followed by a framed painted window
    (bridge, village, Momo) with the child's home in a phone. Desktop is unchanged.
  - **Quotation**: the other-language promise is shown as a quotation (“…” or «…»), without its closing `.`/`។`.
  - **Ages**: 3–12 in landing, sign-in, README and PRODUCT copy. The seeded "Junior (4–9)" age hub is curriculum data
    and stays.
  - **Statements**: `site_statements` (V63), editable in Operations → Landing page. A TESTIMONIAL needs attribution,
    a source note and confirmation that it is genuine. Changes are audited, with optimistic versioning. Three existing
    brand lines are seeded.
  - **Public demo**:
    - `public_demo_activities` (V63) holds up to 3 exact published platform versions;
    - `GET /demo/activities` and `POST /demo/check` store nothing, the check is limited to 60/min per client, and
      anything outside the demo is refused with 404;
    - `/try` reuses the child's question view and verdict (`AnswerVerdict` extracted from `PlaySession`; learner reads
      go through the extracted `LearnerActivityReader`);
    - the admin picker is searchable server-side (chosen first, then 12 matches).
  - **Tests**:
    - `PublicDemoAndStatementsIntegrationTests` 3/3;
    - the learner-read suites that use the refactored reader: QuestionGuidance 3, QuestionMediaAndOrdering 4,
      ContentWithdrawal 2, LearningJourneyApi 8, ReadQueryScaling 1, CrossFamilyAccessSweep 1;
    - ServerTextCoverage 3, AuthRateLimit 5;
    - Vitest: child, marketing, admin operations and i18n, 20 files / 157 tests, including `m2.test.tsx` (5) and
      `quote.test.ts`;
    - `tsc`, ESLint;
    - browser checks: hero and statements at 375/768/1440 in EN and KM (no horizontal scroll), `/try` played
      wrong-then-right at 375 and 1280 with no page errors, admin page at 1280 and 390.
- 2026-10-22: **M3 done.**
  - **Setting**: a parent chooses a daily learning time per child (15, 20, 30, 45, 60, 90 or 120 minutes; off by
    default) in the child's *School & settings* tab. It is stored in the existing `child_settings.daily_time_limit_minutes`
    column (V9), which nothing had written; V64 adds a CHECK for the allowed lengths.
  - **Time used**: computed by the server from its own timestamps (`DailyLearningTimeService`). Each attempt started
    today counts from its start to its last answer or completion, capped at 30 minutes, so a forgotten open activity or
    the device clock can't stretch it. An attempt with no answer yet counts 0. The learning day follows
    `platform.learning.day-zone` (default `Asia/Phnom_Penh`). A per-family time zone is not built; there is no
    family time-zone field yet.
  - **Graceful stop**:
    - once the time is used, starting a *new* activity is refused with 409 `dailyLimitReached`, with limit, used and
      extra minutes;
    - resuming an open activity is never refused (the check runs after the in-progress reuse), and answers are never
      refused;
    - the child sees a calm "That's all for today!" screen with the buddy, links to the shelf and the path, and a line
      saying a grown-up can give more time behind the lock;
    - the home shows a quiet notice;
    - the activity intro says "This is the last activity for today" when 5 minutes or less are left.
  - **More time**: the parent can give +15 or +30 minutes for today (the API also accepts 10), at most 2 hours extra a
    day. Each grant is recorded in `child_daily_extensions` (V64) and audited as `CHILD_DAILY_TIME_EXTENDED`. Only
    the child's family can read or change any of it.
  - **Family data**: the export includes the limit and every extension. Erasure deletes `child_daily_extensions`
    (CHILD_TABLES closure kept).
  - **Not done**: the add-a-child suggestion for under-6s waits for D-08.
  - **Tests**:
    - `DailyLearningTimeIntegrationTests` 2/2 over HTTP and PostgreSQL. They cover setting the limit, an invalid
      length, time used, the refused start, resume and answers still accepted, a refused bad extension, the
      extension and its audit, the 2-hour cap, another family refused, and turning the limit off;
    - FamilyErasure 6/6, FamilyDataExport 2/2, ActivityAttemptServiceTests 8/8, ServerTextCoverage 3/3;
    - Vitest: full suite green, with new tests for the path and practice rest screens, the last-activity note, and the
      parent section (set, figures, extension);
    - `tsc`, ESLint;
    - browser on the isolated dev stack (V64 applied):
      - a parent set 15 minutes and a finished 16-minute activity used the day;
      - playing again showed the rest screen and the home showed the notice;
      - the parent gave +15 and the child could start again.
  - **CI fix for M2** (found on 764d034):
    - the admin demo picker put the chosen version in the same title-ordered `LIMIT 13` as every match, so enough
      published platform activities with titles sorting before it pushed it off the list (the public demo still
      served it);
    - the chosen versions are now read on their own;
    - regression test `aChosenActivityStaysListedHoweverManyTitlesComeBeforeIt`, failing on the old code, 4/4 with
      the fix.
  - **Next**: M4.
- 2026-10-23: **M4 done.**
  - **Principal sign-up**:
    - a principal signs up, then **confirming their email opens the account** (`AccountRecoveryService.
      confirmVerification`, audited `PRINCIPAL_SELF_ACTIVATED`), with no administrator approval;
    - a password reset alone doesn't open it;
    - principals still pending who had already confirmed before this change stay in the administrator's approval list,
      which remains the fallback;
    - the register form no longer asks a principal for a school name it silently dropped; the name is asked for after
      sign-in.
  - **Setting up a school**: `SchoolService.setUpSchool` is safe to repeat. The same principal and name (trimmed,
    case-insensitive, not archived) returns the existing school with 200 instead of 201. It is serialized on the
    principal's account row and audited `SCHOOL_CREATED`. Names are limited to 200 characters.
  - **Plans**:
    - SCHOOL plans may be self-service **once priced** (the Y35 rule is replaced);
    - the administrator's plan editor has the school checkbox;
    - a principal sees the self-service school plans, each with its price and limits, when no paid plan is running,
      and pays by a method that is switched on;
    - renewal of the school's own plan is unchanged;
    - switching plans while a paid plan runs is refused (409, "ask the Bookeyond team"), because proration is a
      pricing decision;
    - nothing opens until the payment is confirmed (Y44 unchanged);
    - a never-paid school is told "Choose a plan to open …" instead of "paused".
  - **Tests**:
    - new `SelfServiceSchoolOnboardingIntegrationTests` (2): duplicate and concurrent set-up, unpriced
      self-service refused, choices, the arranged plan refused, two concurrent pay clicks making one order, pending
      granting nothing, a stranger refused, confirmation opening the school with the plan's class limit, switching
      refused while paid;
    - `AccountRecoveryIntegrationTests.confirmingTheAddressOpensANewPrincipalsAccount`. It replaces
      `confirmingTheAddressNeverApprovesAPendingPrincipal`, deliberately: the behaviour changed by this program's
      design;
    - 62 of 63 in the affected backend suites: SchoolPaymentAccess, Payment, SchoolPlanAndStorage, SchoolStaff,
      ServerTextCoverage, PublicRegistrationRoleSecurity and AccountRecovery all pass. The one failure,
      `SubscriptionIntegrationTests.aFamilyStops…`, saw a single curriculum in the long-lived local `bk_test`
      database. That is environment: CI uses a fresh database, and M4 doesn't touch curricula;
    - Vitest, including the new `register.test.tsx` and principal plan-choice tests; `tsc`, ESLint;
    - E2E `school-journey.spec.ts` updated to the new flow (not run locally).
  - **Runtime and browser** (isolated stack):
    - sign-up showed "Check your email";
    - sign-in was refused before confirming;
    - the emailed link confirmed the address and sign-in worked with no approval;
    - "Set up your school" led to "Choose a plan to open …" with two test plans, prices and limits;
    - paying for the second by bank transfer created a pending order with the test instructions;
    - the API showed the same name returning 200, a class refused while pending (402), the administrator's
      confirmation, the school paid for a year, a class created (201), and only renewal offered.
  - **Next**: M6.
- 2026-10-23: **M6 done.**
  - **Refusals carry a reason**:
    - a plan-limit refusal (409) now carries `reason: PLAN_LIMIT`, `audience` (FAMILY or SCHOOL) and `dataKept`;
    - for a **family's** limit it carries `unlockedBy`: up to 3 real plans a family may choose (active, self-service,
      family) that include more or have no limit, smallest step up first, with a price only when the plan has one
      (`PlanOffers`);
    - a school's limit never lists plans: a family can reach it by giving a school access to a child, and the school's
      plans are its principal's (shown on the access-checked plan page);
    - a school payment refusal (402) carries `SCHOOL_UNPAID` or `SUBSCRIPTION_EXPIRED`.
  - **Not built**: `FEATURE_NOT_IN_PLAN` and `PURCHASE_UNAVAILABLE`. Plans have only counted limits, with no feature
    flags to report, and an empty `unlockedBy` already says no plan with more is offered.
  - **Offers never decide the refusal**: if the plans can't be read, the refusal is answered without them (logged).
  - **Frontend**:
    - `PlanLimitNote` shows what is kept, the offers (name, what it includes, price) or "no plan with more is offered
      right now", and the link to the plan, in the add-child, review and choose-subject flows;
    - the principal's add-class and invite-teacher refusals add "nothing the school has is removed" and the plan link.
  - **CI fix**: `SchoolPaymentAccessIntegrationTests` asserted that no school plan is offered anywhere. Since M4, other
    tests' self-service school plans are offered to every unpaid school, so it now asserts the intent: the arranged
    plan isn't offered or payable.
  - **Tests**:
    - `PlanOffersTests` (2, unit): choice, order, cap, school and empty cases;
    - `EntitlementNoticeIntegrationTests` (2): family refusal shape and exclusions, school 402 reason with no plans;
    - SchoolPaymentAccess 3/3, SelfServiceSchoolOnboarding 2/2, ServerTextCoverage 3/3;
    - Vitest full suite, with the new `planLimit.test.tsx` and the principal limit-note assertion; `tsc`, ESLint.
  - **Browser** (isolated stack): the M3 test family on a one-child plan tried to add a second child and saw the
    server's sentence, "nothing … changes", three plans with prices, and the link. The first build listed every
    self-service plan in the database (dozens of E2E leftovers), which is why there is now a cap of 3.
  - **Next**: M7.
- 2026-10-23: **M7 done.**
  - **Paged lists** (the project's `PageResponse`, page from 0; deliberate contract changes, both sides and their tests
    updated):
    - `GET /account/devices?page&size` (default 6, at most 20) puts this device first, then the most recently active;
    - `GET /family/payments` and `GET /schools/{id}/payments` (default 10, at most 20) are newest first, with the id
      as tie-break so pages never overlap. They replace a silent cut at 20, so every payment is reachable now;
    - a size over 20 is a 400, not a silent clamp.
  - **Devices are cut from the whole list**: a person's live sign-ins are few because they expire, and slicing keeps
    "this device" first on page 0.
  - **Frontend**: the existing `Pager` sits under devices, the family's payment history and the school's invoices.
    The pending payment is read from page 0; it is always the newest, because no new payment starts while one waits.
  - **Not paged, with reasons**:
    - an order's event ledger is bounded by its few state changes;
    - the admin audit log is already paged;
    - a class's children are bounded by class size and the school plan's child limit. Revisit if schools exceed about
      60 a class.
  - **Notifications**: none built, because no producer needs them yet.
  - **Tests**:
    - `AccountSettingsIntegrationTests` 9/9, with the new `theDeviceListIsPagedWithThisDeviceFirst`: 8 sign-ins in
      pages of 3, no overlap, all reachable, an empty page beyond, size 21 refused, another account isolated;
    - `PaymentIntegrationTests` 4/4, now paging a family's 2 payments newest first, with size 21 refused and another
      family seeing none;
    - SchoolPaymentAccess 3/3;
    - Vitest full suite (60 files, 327 tests), with a device paging test that asks the server for page 1; `tsc`.
  - **Browser** (isolated stack): the test parent with 9 live sign-ins saw 6 devices, "This device" first, and
    "1–6 of 9" with Previous and Next.
  - **Next**: M10 (child access across devices, trusted caregivers).
- 2026-10-23: **M10a done: a child's own device.** M10 is split: caregivers (M10b) touch every family endpoint and
  get their own milestone.
  - **Flow**:
    - a parent with access to the child makes a code (6 characters without 0/O/1/I/L, 15 minutes, one at a time per
      child; a new code ends the last);
    - the other device types it at `/child-device` (case, spaces and dashes ignored) and waits with a secret only it
      holds;
    - the parent sees which device waits (named from its user agent) and approves or cancels;
    - the device then collects, once, a sign-in for **that child only**.
    - Only SHA-256 digests of the code and the secret are kept.
    - A wrong, used, replaced, expired or cancelled code fails the same way (400; 409 once it has ended) and says
      nothing about any child.
    - The public steps are `/api/v1/auth/child-device/{claim,collect}`, rate limited per network at 10 and 60 a
      minute.
  - **The child's sign-in** is the parent's account narrowed to one child:
    - `account_sessions.child_id` (V65) is carried through refresh rotation, so the scope never widens;
    - the access token carries `cid`; its authority is `CHILD_DEVICE`, never a role;
    - `ChildDeviceScopeFilter` allows only that child's learner endpoints (the child, enrolments, skills, today, the
      break setting, placing reviews, buddy and language, journey and next step of the child's own enrolments,
      published content for that child, the child's own attempts, refresh and logout). It checks enrolment and
      attempt ownership in the database and fails closed;
    - refresh never returns the parent's email to the device.
  - **Limits and revocation**:
    - at most `CHILD_DEVICES_MAX_PER_CHILD` (3, D-05 pending) devices per child;
    - the parent's device list labels each one "{child}'s space only" and signs it out immediately (existing
      session liveness);
    - a device whose sign-in ended goes back to the code page, not the grown-ups' login.
  - **Child device UI**: on the device the grown-up gate is hidden, every other route redirects to the child's space,
    and the rest screen says a grown-up can add time "from their own phone".
  - **Erasure** deletes the child's codes and device sign-ins (CHILD_TABLES closure kept). Audit:
    `CHILD_DEVICE_CODE_CREATED`, `CHILD_DEVICE_APPROVED`, `CHILD_DEVICE_SIGNED_IN`, `CHILD_DEVICE_CODE_CANCELLED`.
  - **Tests**:
    - new `ChildDeviceLinkIntegrationTests` (2). The flow test sweeps 26 refused calls (sibling, parent pages,
      settings, time extensions, device codes, billing, account, export, catalogue, schools), plays a whole activity
      through the child sign-in, checks that refresh stays scoped and the email is hidden, and that signing out is
      immediate. The second test covers expired, replaced, cancelled and unapproved codes and the three-device limit.
      Mutation check: without the filter the sweep fails at the first sibling read;
    - FamilyErasure 6/6, SessionReliability 21/21, SessionLiveness 3/3, AccountRecovery 21/21, AccountSettings 9/9,
      AuthRateLimit 5/5, CorsPolicy 10/10, Actuator 1/1, CrossFamilyAccessSweep 1/1, ServerTextCoverage 3/3,
      DailyLearningTime 2/2;
    - Vitest full suite (61 files, 334 tests), with the new `childDevice.test.tsx`: the parent's code and approval,
      the device typing, waiting and landing only in the child's space, a wrong code; `tsc`, ESLint.
  - **Browser** (isolated stack, V65 applied):
    - the parent made a code on the child's settings page;
    - this browser, cleared, typed it at `/child-device` and waited;
    - the parent approved it (over the API, the parent side meanwhile);
    - the device opened Dara's space by itself with no grown-up gate, `/parent/account` came back to the child's
      space after a reload (the scope survived refresh), and a book opened;
    - the parent's list showed the device as Dara's, signing it out ended it on the next load, and the device then
      went to the code page;
    - at 375 px there is no horizontal scroll.
  - **Risks**:
    - device user agents are shown to the parent as reported, so a device could misname itself; the parent approves
      knowing a code was just typed;
    - codes are 6 characters from 31, guarded by 15 minutes, single use, parent approval and the rate limit.
  - **Next**: M10b (trusted caregivers).
- 2026-10-23: **M10b done: trusted caregivers.**
  - **Inviting**:
    - a member of a child's family invites an adult by email **for that one child**, as either "see progress"
      (VIEW_PROGRESS) or "see progress and open the learning space" (OPEN_SPACE) (`child_caregivers`, V66);
    - the person sees the invitation on their home page after signing in with that address, and accepting needs the
      address **confirmed** (the teacher-invitation pattern, with no link token);
    - not yourself, not a family member, one live invitation per child and address, at most
      `CAREGIVERS_MAX_PER_FAMILY` (2, D-05) distinct people per family, serialized on the family row;
    - the invitation email names the child, never the parent's address.
  - **Access**: a caregiver is **not a family member**, so `FamilyAccessGuard` still refuses them everywhere it
    applies. The new `ChildAccess` admits them only where named:
    - viewer: the child, enrolments, journey, skills and one skill, changes, activity history, insights and their
      history, progress;
    - learner: viewer plus placing reviews, buddy and language, today's time and the break setting, next step,
      content, and attempts;
    - parent-only: editing the child, the daily limit and extensions, break settings, starting and stopping subjects,
      generating insights, caregivers, device codes, streak, export and lists.
    - A caregiver sees the child without date of birth or deletion date (`access` on the child response).
    - The family changes the scope or removes them at once; a caregiver can stop helping.
  - **Data**: the family's export lists each child's caregivers. Erasure deletes a child's caregiver rows, and an
    account's erasure deletes its caregiving and the invitations to its address. Audit: `CAREGIVER_INVITED`,
    `_ACCEPTED`, `_DECLINED`, `_SCOPE_CHANGED`, `_REMOVED`, `_LEFT`.
  - **UI**:
    - "People who help" on the child's settings (invite, scope, remove);
    - "Children you help with" on the home (accept, decline, see progress, open the space, stop helping);
    - `/parent/helping/:childId` (the family's progress sections, read-only, plus "Open {child}'s space");
    - the family's child pages send a caregiver to it.
  - **Fixed on the way**:
    - a `ChildAccess` that caught the family guard's refusal left the transaction rollback-only (500); it now asks
      directly;
    - a duplicate ServerText entry broke its map;
    - the M10a sweep's export call used a wrong path, which the scope filter hid; it now uses the real one.
  - **Tests**:
    - new `CaregiverIntegrationTests`: invite rules, confirmation, a stranger refused, viewer reads, 16 refused calls
      plus a valid start refused for viewers, a whole activity played by an OPEN_SPACE caregiver, the per-family
      limit, removal ending access at once, export contents, audit. Mutation check: letting viewers act as learners
      fails it at the first learner read;
    - ChildDeviceLink 2/2, FamilyErasure 6/6, FamilyDataExport 2/2, CrossFamilyAccessSweep 1/1, LearningJourneyApi
      8/8, ParentInsightTruthfulness 5/5, DailyLearningTime 2/2, ContentWithdrawal 2/2, QuestionGuidance 3/3,
      ServerTextCoverage 3/3;
    - Vitest: full suite plus `caregivers.test.tsx` (6); `tsc`, ESLint.
  - **Browser** (isolated stack, V66 applied):
    - the parent invited a second adult from Dara's settings;
    - the invitation email arrived;
    - signed in as that adult, the home showed the invitation and accepting turned it into "Dara" with progress and
      the space;
    - the helping page showed Dara's real progress, a book opened in Dara's space, and the family's settings URL
      redirected to the helping page.
  - **Risks**: the reused progress sections and server-written insights still say "your child" to a caregiver
    (wording, follow-up). The caregiver acts under their own account, and attempts they play are the child's evidence
    as with a parent.
  - **Next**: M8 (payment QR, reconciliation, secure grants).
- 2026-10-23: **M8a done: step-up for money and access, and reconciliation.** M8 is split: M8b
  (administrator-configured instructions and the genuine KHQR image) is next.
  - **Step-up authentication**:
    - `POST /account/step-up {password}` returns a 5-minute proof. It is a JWT typed `step-up`, carrying the account
      and this sign-in's `sid`, and it can never be used as an access token, nor an access token as a proof;
    - a wrong password counts towards the lockout, as at sign-in (422);
    - `@RequiresStepUp`, checked by `StepUpInterceptor` before the controller (so nothing runs first), guards:
      - payment confirm, reject and refund;
      - a family's or school's plan given, its period changed or ended;
      - plan activate, retire and make-default (they change prices or every family's limits);
    - without a valid proof for this account and sign-in, the answer is 403 `stepUpRequired`;
    - not guarded, because none moves money or access: draft plan edits, declining a request, creating a pending
      invoice, provider reconciliation (the provider's answer decides);
    - audited `ACCOUNT_STEPPED_UP`;
    - the frontend `StepUpProvider` asks for the password once and retries the original request once with
      `X-Step-Up`. The refused attempt was stopped before the handler, so nothing doubles. The proof is held in
      memory only, never in storage, URLs or logs (checked).
  - **Reconciliation**:
    - confirming a transfer now needs the receipt's amount (minor units) and currency, equal to the order's frozen
      price, else 409;
    - one receipt settles one payment per way of paying (case and spaces aside), else 409. This is checked under a
      transaction advisory lock on the receipt, so concurrent confirmations can't both pass;
    - no unique index: existing databases already hold duplicate test receipts, and a migration would fail on them;
    - the admin form asks for the amount and currency as the receipt shows them, never filled in from the order.
  - **Tests**:
    - new `StepUpAndReconciliationIntegrationTests` (2): no proof, wrong password, the proof as an access token and
      from another sign-in all refused; activate needs it; wrong amount or currency refused; receipt reuse refused;
      two concurrent confirmations with one receipt end as 200 and 409. Mutation check: dropping the receipt rule
      fails it;
    - new `StepUpTokenTests` (2, unit): binding to account, sign-in and issuer, expiry, never an access token;
    - the shared `TestAdmin` steps test administrators up and builds receipts from the order. Payment 4/4,
      SchoolPaymentAccess 3/3, SelfService 2/2, SchoolPlanAndStorage 5/5, Subscription 9/9, EntitlementNotice 2/2,
      EmailLanguage 6/6, AccountSettings 9/9, SessionReliability 21/21, ChildDeviceLink 2/2, ServerTextCoverage 3/3;
    - Vitest 65 files, 349 tests, including `stepUp.test.tsx` (ask, wrong password, retry with the proof, cancel),
      `minorUnits.test.ts` and `paymentConfirm.test.tsx` (malformed amount caught locally, mismatch shown, exactly one
      retry with the proof, the proof reused within its window);
    - E2E specs updated (`confirmTransfer`, `enterPasswordAgain`, step-up in `ensurePricedSchoolPlan`) and
      type-checked, not run.
  - **Browser** (isolated stack):
    - an administrator confirmed a real pending $5.00 transfer with $4.99;
    - the password dialog appeared, a wrong password was refused, and the right one retried;
    - the server refused the amount; $5.00 then confirmed without a second prompt;
    - the ledger shows one `MANUAL_CONFIRMED`;
    - the Khmer refusal was checked over the API;
    - no proof was found in storage, the URL or the server log.
  - **Next**: M8b.
- 2026-10-23: **M8b done: bank-transfer instructions set in the app, with the owner's KHQR.**
  - **Versions**: `payment_instruction_versions` (V67) is append-only, one version per save: enabled, English and
    Khmer instructions (≤ 2000 characters each), and an optional KHQR image (a platform media-library picture,
    restrict foreign key).
    - Until the first version is saved, `PAYMENTS_MANUAL_*` apply, shown as "set by the server's environment". There
      is no write at startup.
    - Each new MANUAL order records its version (`payment_orders.instructions_version_id`), so a change never
      rewrites an issued invoice.
  - **Saving**:
    - an administrator saves through `PUT /admin/payment-instructions` (`@RequiresStepUp`) with `expectedVersion`. A
      stale or concurrent save is a 409, serialized by an advisory lock;
    - offering without English words is a 400;
    - the image must be the platform's own (no teacher's picture), not archived, and a picture. Upload type and size
      are the existing media inspection (PNG, JPEG, WebP, ≤ 2 MB);
    - audited `PAYMENT_INSTRUCTIONS_CHANGED` with version, flags and lengths, never the account text;
    - a QR in any version can't be deleted from the media library (409, "archive it instead").
  - **Customers**:
    - the checkout of their own pending order carries `instructionsKm` and a short-lived signed `qrImageUrl`;
    - `TransferInstructions` shows the words in the reader's language (English when there is no Khmer) and the QR
      beside the exact amount and the reference;
    - it says paying or a screenshot doesn't start the plan. Nothing in M8b confirms or grants: confirmation is still
      M8a's administrator check against the receipt.
    - Switching it off removes bank transfer from the offered methods and refuses new transfers (409). Instructions
      for pending orders are shown only while the method is offered (the existing rule).
  - **Not built**: the customer screenshot upload ("evidence for review"), deferred. The customer's own payment note
    remains. ABA PayWay is untouched and still unverified against ABA.
  - **Tests**:
    - new `PaymentInstructionsIntegrationTests`:
      - the environment fallback; teacher and parent refused; step-up required;
      - a teacher's picture refused (400), a missing one 404, a non-picture rejected at upload; blank or long text
        refused;
      - a stale version 409; the family checkout in both languages with a working signed QR and still PENDING;
      - an issued invoice keeps version 1 after version 2; a new order gets version 2;
      - the QR can't be deleted; two concurrent saves end as 200 and 409; switching off removes MANUAL and refuses
        new transfers; four audit rows without the account text;
      - it cleans up, so other tests keep the environment's instructions.
      Mutation check: serving the latest version to every order fails it;
    - PaymentContract 4/4 (the manual method over an instructions service with no saved version), MediaLibrary 1/1,
      MediaRecovery 4/4, Payment 4/4, StepUpAndReconciliation 2/2, FamilyErasure 6/6, FamilyDataExport 2/2,
      ServerTextCoverage 3/3;
    - Vitest 66 files, 356 tests, with `transfer.test.tsx` (QR beside amount and reference, Khmer words or the English
      fallback, the panel taking over from the environment with an uploaded picture).
  - **Browser** (isolated stack, V67 applied):
    - the administrator saw "set by the server's environment", wrote both languages, uploaded a picture through the
      real media endpoint, and saved with the password prompt as version 1;
    - after a backend restart, version 1 was still current;
    - a family's new transfer showed the QR (it loaded), the amount, the reference and the words, still PENDING.
  - **Next**: per the parent-and-child-first order, M9 (phone registration foundations, admin TOTP) or M5
    (authoring/upload/visibility). See the reconciliation note in the next entry.
- 2026-10-23: **Roadmap reconciliation after M8.**
  - **Done and in CI**: M1–M4, M6, M7, M8 (a, b), M10 (a, b). CI on M8a failed in E2E, a locator ambiguity and a
    step-up after a page load; fixed in `da71a83`, with the full E2E suite 39/39 locally.
  - **Remaining, by the parent-and-child-first rule and dependencies**:
    1. **M9a: administrator TOTP**. M8 says grants and payment-method changes need a second factor. No external
       dependency.
    2. **M5: authoring UX, upload, visibility**. Form uploads exist (`MediaSlot`, the media library). Missing:
       drag-and-drop with progress and preview, orientation in the builder, and PRIVATE/SCHOOL/PUBLIC visibility.
       Content quality is what children see.
    3. **M9b: phone registration foundations**. Normalization, the `SmsSender` abstraction with a test sender
       refused in prod, codes. It can't run for real until D-04 (SMS provider).
    4. **M11**: storage-provider abstraction (BYOS waits for D-06).
    5. **M13**: integration and readiness pass.
    6. **M12**: school management, deferred until the family milestone is agreed.
  - **Age targeting**: curriculum-driven age targeting exists (age hubs, `forChildAge` in the family catalogue).
    Content for ages 3 and 10–12 waits for D-09.
  - **Backlog** (small, folded into a fitting milestone):
    - ~~caregiver wording~~ (done with M5b, 2026-10-24);
    - ~~the duplicate reference line in a pending payment~~ (done in the family pass, 2026-10-24);
    - the customer payment-screenshot upload (deferred from M8b);
    - ~~a per-family learning-day time zone (M3)~~ (done in the family pass, 2026-10-24);
    - ~~the account-setup page's role wording~~ (done in the family pass, 2026-10-24).
- 2026-10-23: **M9a done: an administrator's authenticator app (TOTP) for step-up.**
  - **What**:
    - RFC 6238 codes (HMAC-SHA1, 6 digits, 30 s, one step of drift), no new library (`Totp`, checked against the
      RFC's test vectors).
    - V68: `account_second_factors` (secret encrypted with AES-GCM under a key derived from `JWT_SECRET` for this
      purpose; PENDING until a first code; `last_used_step`; `failed_attempts`) and `account_recovery_codes`
      (8 per activation, SHA-256 digests only).
    - An administrator's step-up (M8) needs the password **and** a code or an unused recovery code; without an app
      on, it is refused (403 `secondFactorSetupRequired`) - the roadmap's rule that grants and payment changes need a
      second factor. Families' step-ups are unchanged.
    - A code is never accepted twice; wrong codes are counted apart from the sign-in count (the right password with
      each one resets that) and lock the account at the same limit (`SECOND_FACTOR_LOCKED`).
    - Account page → "Authenticator app" (administrators only): password, then the key (grouped, copyable, and an
      `otpauth://` link for a phone), then a first code; the recovery codes are shown once (download as text).
      Turning it off needs the password and a code. Another administrator, stepped up, can turn a colleague's off
      (`SECOND_FACTOR_RESET`); never their own.
    - The step-up dialog asks an administrator for the code at once (and anyone the server asks), in English and
      Khmer; it points an administrator without an app to setting one up.
    - Adding staff (`POST /admin/staff`, which can create administrators) now needs a step-up: it gives access.
    - Erasure removes the two tables' rows; DEPLOYMENT.md covers a lost authenticator and `JWT_SECRET` rotation.
  - **Not done (decisions)**: a QR code for setup (needs a QR library, D-12; the key and the `otpauth://` link work
    in every authenticator app); a second factor at sign-in itself (the roadmap asks it for grants and payment
    changes; sign-in stays password-only); listing which administrators have an app on.
  - **Verified**:
    - integration: `SecondFactorIntegrationTests` (4: setup, pending not in force, wrong code, recovery codes as
      digests, replay refused, recovery code once, lockout at the limit, families unaffected, turn off, colleague
      reset with step-up, never own, audit without the key) plus every suite that steps up an administrator - 19
      suites, 82 tests; `TotpTests` (RFC vectors, drift, replay, base32);
    - mutation: removing the code check from step-up fails 3 of the 4 new tests;
    - Vitest 362/362 (new: the dialog's code prompt and setup pointer; the account section's setup and turn-off);
    - E2E locally against the isolated stack (V68 applied): language (Khmer staff creation with the dialog in
      Khmer), plans (3 step-ups), school invoice payment - 6/6; the helpers turn on the E2E administrator's app
      and keep its key in the git-ignored `frontend/.e2e-auth/`;
    - browser: a new administrator set up the app on the account page with a code computed from the key shown,
      saw 8 recovery codes once, then added a staff member through the dialog's password and code; the key isn't
      in the backend log, the stored secret isn't the key, the audit holds counts only; phone width has no
      horizontal scroll.
  - **Next**: M5 (authoring, upload, visibility), per the reconciliation order.
- 2026-10-23: **M5a done: uploading inside the authoring form.**
  - **What**: every picture or recording slot in the question form (`MediaSlot`: prompt, reading, choices,
    matching, ordering and sorting items) takes a dropped file as well as browse and the library. While it uploads it
    shows the file (a preview of a picture, its size and pixel size) and a progress bar (`role=progressbar`, "Checking
    the file…" once sent), and it can be cancelled. An obvious mismatch (a recording on a picture) is said before
    anything is sent; everything else is the server's decision, as before - the same `POST /studio/media` with its
    ownership, allowance, deduplication and byte inspection. A refused or cancelled upload says so and that the rest
    of the question is as it was; nothing else in the form changes.
  - **How**: `apiClient` sends a request with `onUploadProgress` over XMLHttpRequest and answers it as a `Response`,
    so refresh, step-up and errors take the same path as every request; `studioApi.uploadMedia(file, onProgress,
    signal)`. The library and the payment-instructions panel upload as before.
  - **Verified**: Vitest 366/366 (new `mediaSlot.test.tsx`: drop, progress, the server's refusal, cancel, a recording
    never sent to a picture slot; `media.test.tsx` now expects the progress and cancel arguments); E2E Studio journeys
    4/4 (desktop and phone; the first uploads a picture through the slot); browser: in a new question, a dropped
    4.8 MB picture was refused by the server ("at most 2 MB") with the typed question kept, then a 640 × 480 picture
    uploaded through the real endpoint and showed from its signed address, the typed question still there.
  - **Not done here**: the builder's orientation (where an item sits, who will see it, what publishing does) moves to
    M5b with visibility, since "who will see it" is what M5b decides. No client-side size limit is shown before
    upload: the limits live on the server (2 MB pictures) and its message is shown.
- 2026-10-24: **M5b done: a lesson shared with a school, copied to own (D-13; PUBLIC deferred, D-14).**
  - **What**:
    - V69: `lessons.visibility` (PRIVATE | SCHOOL; platform lessons can't be SCHOOL, by a CHECK) and the credit
      `copied_from_lesson_id` / `copied_from_account_id` (SET NULL if either goes). Forward-only: existing lessons
      become PRIVATE, which is what they were.
    - Only a lesson's author shares it (an administrator or a principal who may edit it can't). Colleagues are the
      active staff and principal of a school both belong to that is open and paid (Y44: school services need a paid
      school); anyone else gets 404 for the lesson, as if it weren't there.
    - "Shared by colleagues" in a teacher's Studio lists colleagues' shared lessons with a published activity (titles,
      the school, question counts; no answers). "Copy to my lessons" makes a private lesson of drafts: each published
      activity's current version, its skills, its exact question versions (prompt, answer, choices, pairs/items/groups,
      media, hint, explanation, skills, translations). Pictures and recordings become the colleague's own media
      rows over the same stored file, counted in their storage allowance; a copy that wouldn't fit is refused whole.
      The copy is published through the same checks as anything the teacher writes; nothing reaches a class until
      they do. Sharing never changes a child's path (N7 is unchanged).
    - Audited: `LESSON_VISIBILITY_CHANGED`, `LESSON_COPIED` (counts only). English and Khmer.
  - **Verified**:
    - `LessonSharingIntegrationTests` (2): private until shared; only the author shares; PUBLIC refused (400);
      a colleague at the paid school and its principal find it, a teacher at another school, a teacher sharing only
      an unpaid school and an administrator don't, and can't copy it (404), nor can the author copy their own;
      the copy is the colleague's, private, credited, drafts, with the same content, their own media (one row per
      file), the Khmer translation; they publish it; the source is untouched and keeps its media; editing and
      publishing the source afterwards leaves the copy as copied; unshared or the school archived → not found;
      no storage room → 409 with nothing created; a platform lesson can't be shared (409);
    - mutation: dropping the paid-school condition fails the first test;
    - Vitest 373/373 (new `sharing.test.tsx`); full backend suite (see the commit);
    - browser, on the isolated stack after the preview seed: a teacher set "Teachers at your school" (kept after a
      reload); a second teacher at that school saw it under "Shared by colleagues" and copied it: "Copied", "Only
      you", the activity a draft, the message saying so; phone width without overflow; the same screen in Khmer.
      (The second teacher's school membership was a fixture row in the isolated dev database; the rule itself is
      covered by the integration test.)
  - **Not done**: builder orientation beyond what Y42 already shows (where an item sits and what publishing does
    are already on the activity page; "who will see it" is now the lesson's control). PUBLIC (D-14).
  - **Fixed alongside**:
    - the review journey (CI runs it with review on) adds an administrator through the Staff page, which since M9a
      asks for the password and a code; the spec now answers it. Running it on desktop then phone also showed that
      Playwright starts each project in a new worker, which forgot the codes already spent, so the server (rightly)
      refused a replayed code: the E2E helper now keeps the last time step used beside the key;
    - **caregiver wording** (backlog since M10b): a caregiver's page no longer calls the child "your child". Insight
      sentences name the child for a reader outside the family (server-side, by `ChildAccess`; English and Khmer, the
      name followed by a space in Khmer, which writes "your child" joined to the next word); the skills section names
      the child for everyone; the skill page's fallbacks say "this child". `InsightTextTests` covers it.
- 2026-10-24: **Recorded risk (M9a): authenticator keys are sealed with a key derived from `JWT_SECRET`.**
  - Rotating `JWT_SECRET` (e.g. after a leak) also makes every administrator's authenticator unreadable; they set it
    up again (DEPLOYMENT.md says how, including the operator step when no administrator can step up).
  - Safer before production: a dedicated `SECOND_FACTOR_KEY` (or a KMS key) with a key id stored beside each
    ciphertext, so keys rotate on their own schedule (old ids kept for reading, new writes on the new key, an explicit
    re-encryption job). Not done now: it needs a deliberate migration plan for existing secrets and a deployment
    secret the owner provides; the current behaviour is documented, not hidden.
  - The administrator second-factor policy stays as built in M9a: step-up actions need an authenticator code;
    sign-in itself is password-only. Changing either is a product decision.
- 2026-10-24: **Family-first pass (owner's brief: parents and children first).** CI green on `4f93014` (M5b closed).
  - **Walked through in a browser** as a seeded parent and child on the isolated stack: the family home, a child's
    learning plan (why the next step), skills and evidence, school and settings (daily time, breaks, another device,
    people who help, deletion), the child's space, an activity with a right and a wrong answer (gentle feedback, the
    right answer shown), the end screen. No coercive mechanics found. Curriculum-driven learning is already in place
    (age groups are data with min/max ages; a child's age is separate from the path; the family chooses, with other
    ages offered separately; enrolment and the planner drive what is played) - not rebuilt.
  - **Found and done**:
    - **The learning day per family** (V70 `families.learning_day_zone`, null = the platform's, so every existing
      family keeps its day): a parent sets it on the account page ("Your family's day", every IANA zone the browser
      knows; "Use this device's" only when that device's midnight differs); the server validates the id, counts each
      child's day from midnight there (limit, used time, extensions), audits the change and includes it in the
      family's data export; "Learning each day" names the place ("midnight (New York time)"). A child device can't
      reach it (the M10a allow-list). A staff account with no family gets 404.
    - **A path for another age** says so on the child's learning plan: "Maths - Junior is for ages 4-9, and Sophea is
      11 now", naming the published platform path in the same subject for the child's age when there is one ("Start
      ..."), else that there is none yet and this one carries on. Server-side, by the same age-group rule automatic
      enrolment uses; for family paths only (a class's path is the school's); nothing changes on its own.
    - A bank transfer's reference shows once, in the transfer box beside the QR (it was also in the header).
    - The family home's "Join a class with a code" named and linked only the first child; with several children it
      now offers each ("Sophea · Rithy"), since a code is joined from the page of the child in that class.
    - The account-setup page and its sign-in note no longer say "teacher account" / "your school's invitation" to
      every invited role (neutral wording, English and Khmer).
  - **Verified**: `DailyLearningTimeIntegrationTests` (+2: the zone, its day boundary at New York midnight - 0 minutes
    before it, 20 after - export, audit, reset, no family → 404; a mutation ignoring the family's zone fails it),
    `AgeFitIntegrationTests` (repeatable, also on a fresh database: automatic enrolment taken into account), export,
    journey, payment suites; full backend 477 (3 proxy-test errors from the local connection limit, passing alone);
    Vitest 379 + new; browser: the zone saved and named in the child's settings, reset; the age note on a child made
    11 (then restored); E2E (see the commit).
  - **Checked, no change**: "Recognize addition may benefit from additional practice" beside "too early to tell" on
    2 answers is consistent: it is the Smart Review recommendation for a review that was actually scheduled
    (`ParentInsightService.reviewInsight`, shown as a Suggestion), not a mastery claim, which needs sufficient
    evidence (Battle 2C).
- 2026-10-24: **M9b-1 done: a verified phone number on an account** (D-12: libphonenumber approved; D-04 SMS provider
  still open).
  - **What**:
    - V71: `accounts.phone_e164` + `phone_verified_at` (E.164 by a CHECK; one account per verified number by a
      partial unique index) and `phone_verifications` (codes as SHA-256 digests of the challenge id and the code, 10
      minutes, 5 tries, ended once: confirmed or replaced).
    - Numbers are normalized with libphonenumber: Cambodian numbers without a country code, any country's with it;
      only numbers that can receive a text (mobile) are accepted.
    - Asking for a code needs the password; once a minute and 5 an hour per account, 5 texts an hour per number
      (429 with retryAfterSeconds). A number verified on another account is answered the same but gets no text and
      can't be verified here (no enumeration); confirming decides one account per number under a lock on the number.
      Removing the number needs the password.
    - `SmsSender`: `none` (default: the account page says texts aren't set up; 503 for a code) or `local-test`
      (each text written to the mail catcher; the app refuses to start with it in `prod`). No provider, no cost.
    - Account page → "Phone number" for every account (add, code, send another, remove), English and Khmer; the
      number shown as people write it. Audited with the last three digits only; in the family's data export; erased
      with the account.
  - **Verified**: `PhoneNumbersTests` (3: Cambodian and other numbers, landlines and junk refused, the production
    guard), `PhoneVerificationIntegrationTests` (2: password, landline, code digest, once a minute, wrong code,
    confirm, once only, export, another account gets no text and can't verify, remove needs the password, audit
    without the number; five tries, ten minutes, five an hour); a mutation sending to a number verified elsewhere
    fails the first; Vitest (new `phone.test.tsx`); browser: a parent added a test number, the code arrived in the
    local mail catcher (never a phone), and confirming made it the account's number.
  - **Next (M9b-2)**: signing in and recovering by a verified phone. Before building, two product questions: may an
    account be phone-only (no email), given that several flows rely on a confirmed email (school access, receipts,
    erasure notices)?; and is a text code enough to reset a password? Asked at that step.
- 2026-10-25: **CHECKPOINT (session context nearly used up).** Commits: `204815c` (test codes from the current TOTP
  window - fixes an intermittent E2E plan-journey failure), `bdfd7c2` (M9b-1), and the phone sign-in commit below.
  - **Sign in with a verified phone** (blueprint: "sign in by email or phone"): the login `email` field (name kept for
    clients) may hold a phone number; anything without "@" is read as a phone number (Cambodian without a country
    code) and matched only against verified numbers; unknown, unverified or junk input answers exactly like an
    unknown address (same 401, same password-hash timing). Login page label "Email or phone number" (EN/KM).
    Verified at runtime on the isolated stack (typed both ways 200; unknown number, junk, wrong password 401; email
    still 200) and by the existing auth suites (43 tests). **No new automated test for it yet** (owner's instruction
    this session) - added later the same day (see the reset entry).
  - **Child learning cycle**: checked, no change needed - completion reads the persisted answers (and hides the score
    if that read fails, never "0 of 0"); the child space shows the path, what's next, the shelf and the garden; the
    parent pages show evidence and why the next step.
  - **Open decision D-15 (ask the owner)**: may an account be phone-only (no email)? 41 code paths read an account's
    email and notices (receipts, erasure, plan changes) go by email; phone-only would mean texts for those (cost once
    D-04 picks a provider). Also: is a text code enough to reset a password (blueprint says "recover by either
    verified channel")?
  - **Exact next actions**: (1) CI check for the last commits; (2) automated test for phone sign-in; (3) password
    recovery by verified phone (code by SMS -> reset), mirroring AccountRecoveryService's limits and no-enumeration,
    if D-15 confirms; (4) then M13 integration/readiness; M11 needs D-06/D-12 (S3 client).
- 2026-10-25: **Password reset by text (D-15: email stays required).** V72 `phone_verifications.purpose` (VERIFY |
  RESET; existing codes VERIFY). `POST /auth/password-reset/phone` always 202 (unknown, unverified or junk numbers,
  disabled accounts, too soon or too often: nothing sent, nothing said; 503 only when texts aren't set up);
  `.../phone/confirm` {phone, code, newPassword}: one refusal message for any wrong, used or expired code (5 tries,
  10 minutes), the password policy checked before the code is used, then as an email reset: other reset links
  withdrawn, lockout cleared, every session ended, "your password was changed" emailed to the account's address
  (the address is not marked confirmed). Rate limits: the request shares the email reset's per-network budget, the
  confirm the login budget. "Forgot your password?" offers "Use my phone number instead" (EN/KM).
  - **Verified**: runtime on the isolated stack (202 for unknown/junk/real; wrong code 422; weak password 422 keeps
    the code; reset 204; the code once; old password 401, new 200; seed restored); suites AccountRecovery 21,
    AuthRateLimit 5, Phone 5, ServerTextCoverage, FamilyErasure; Vitest 388.
  - **Tested** (a method added to the existing `PhoneVerificationIntegrationTests`, no new class): unverified number
    can't sign in; verified signs in written either way; wrong password/junk 401; reset request 202 for unknown, junk
    and real numbers with a text only to the real one; the same refusal for a wrong code and an unknown number; weak
    password keeps the code; the code once; sessions ended; new password works; changed-password email sent.
  - **Next**: CI check; then M13 (role journeys EN/KM, readiness docs) - M11 waits on D-06/D-12 (S3 client).
- 2026-10-27: **L1 "Learn first": an activity can teach before it asks** (owner brief: learning experience first;
  research report [Strong]: explicit model -> guided -> independent; worked examples before practice). V73 adds an
  optional introduction to `activity_versions`: text (<=1200), a picture with its screen-reader description, a
  recording (FKs to `content_assets`, ON DELETE RESTRICT). Draft-only like the rest of a version; a new draft copies
  it; copy-to-own (M5b) carries it with the media remapped; deleting a library file an introduction uses is refused
  ("archive it instead"). Studio: a "Teach it first" panel on the activity (text, picture + description, recording,
  same media rules as questions), shown in "Try it as a child"; publishing is blocked while the picture is
  undescribed (`INTRODUCTION_IMAGE_UNDESCRIBED`). Child: a "Let's learn" card on the activity's first page (picture,
  text, Listen when recorded) and "Show me again" while answering (a dialog; Escape, outside tap or the button close
  it; focus returns; nothing is sent or scored). Activities without one look exactly as before. EN/KM labels; the
  content itself stays in its authored language (no localization layer, unchanged).
  - **Decision taken**: no "no introduction" publish tip - it would flag every review/fluency activity where teaching
    first is not wanted, and the existing exact-warnings contract (`ContentStudioIntegrationTests`) says a minimal
    activity has only `NOT_ON_ANY_PATH`. The panel itself is the prompt.
  - **Browser verified** (isolated stack): admin authored + saved (status shown, readiness and preview refresh without
    reload), undescribed picture blocks publish, described one publishes (v2, v1 retired as usual); new child Dara
    enrolled in Maths - Junior got v2 (the path pins the version current at enrollment; Sophea keeps v1 - unchanged
    behaviour); card shown before questions, signed image 200 with the authored alt; "Show me again" kept a
    half-typed answer, made no API calls; 2 answers recorded, attempt COMPLETED, "2 of 2" from the saved answers; the
    next activity (no introduction) shows no card or button; phone width and Khmer chrome checked. Fixed during
    verification: tiny pictures rendered 1 px (now a fixed frame), card too tall on phones, Escape didn't return focus
    or stop the recording, Studio didn't refresh after saving.
  - **Tested**: Vitest 392; tsc; integration suites ActivityBuilder 3, ContentLifecycle 8, ContentLifecycleStudio 2,
    ContentStudio 9, ContentTenancy 9, LessonSharing 2, MediaLibrary 1, QuestionMediaAndOrdering 4, LearningJourneyApi
    8, ContentWithdrawal 2, ServerTextCoverage 3.
  - **Tests still needed** (no new tests this session, owner's instruction): Studio set/clear introduction incl. media
    rules (other owner's file, archived file, wrong kind) and draft-only; validator error; learner read returns it
    with signed media and never for a draft; new draft copies it; copy-to-own remaps its media; library delete refused;
    Vitest for LearnCard/LookAgain (focus return, Escape, no answer loss).
  - **Content dependency**: only 4 of 524 published questions have an explanation and no activity has an
    introduction yet; the feature is only as good as the content. Khmer introductions need native review (PD-14).
  - **Next**: Priority B (feedback) - see the next entry.
- 2026-10-27: **L2 feedback: authors are told when a question has no explanation.** Research [Strong]: elaborated
  feedback (ES 0.49) far outperforms right/wrong (0.05) and showing the answer (0.32) (Van der Kleij 2015). The
  product already shows an author's explanation after every answer (Y41), but only 4 of 524 published questions have
  one. A question's publish check now lists `NO_EXPLANATION` under "worth a look" - a tip, never a block, so existing
  content still publishes. EN/KM (Khmer drafted, PD-14 review).
  - **Considered, not done**: a second "Show me again" inside the wrong-answer verdict (the button already sits above
    the question; a duplicate adds clutter); "repeated errors -> simpler example / drop a tier" needs the open owner
    decision PD-16 and authored easier variants - not invented here.
  - **Browser verified**: a draft Maths question shows the tip; writing an explanation and saving clears it ("Ready to
    publish"). **Tested**: ContentStudio 9, QuestionGuidance 3, ActivityBuilder 3, QuestionMediaAndOrdering 4,
    ServerTextCoverage 3; Studio Vitest 74; tsc. **Test still needed**: validator emits/clears `NO_EXPLANATION`.
  - **Content gap (owner/educators)**: the platform's Maths seed needs explanations and introductions written and
    reviewed; the code can only prompt for them.
- 2026-10-27: **L3 (D-16) hinted answers are practice, not "shown on their own".** Owner chose "record it; count as
  practice". V74 `answer_records.hint_used` (nullable: older answers stay "not recorded", the immutability trigger is
  untouched). The child's client tells the server when the hint was opened (sent with the answer, pinned to the
  answer's idempotency key; the fingerprint is unchanged when no hint was used). `AnswerSubmissionWriter`: a hinted
  answer is saved and scored as before but calls no `MasteryEngine.recordEvidence` - right **or** wrong (judgment:
  counting only hinted mistakes would teach children that asking for help costs them). Everything that reads
  mastery follows (adaptive choices, Smart Review triggers, parent skill pages' "x of y answers correct"). Smart
  Review's outcome counts only unaided right answers. Parents' recent activity adds "n with a hint - practice, not
  counted as shown on their own"; "How we describe progress" no longer says every answer is evidence. The family
  data export includes `hintUsed`. The child still sees "3 of 3 right" (true) - nothing changes for them.
  - **Runtime/browser verified** (isolated stack, V74 applied): Dara practised "Count the stars": hint opened then
    3 -> stored `hint_used=true`, no skill evidence; next answer without the hint -> `false`, evidence 1/1 on "Count to
    5"; parent recent activity shows the hint line. **Tested** (existing suites, 25 classes incl. LearningLoopCompletion
    9, GoldenLearningJourney, ParentInsightTruthfulness 5, ObjectiveInsightTruthfulness 14, DueReview 3, Idempotency
    11+3, FamilyDataExport 2, FamilyErasure 6, ActivityAttemptService 8); Vitest 392; tsc.
  - **Tests still needed**: hinted answer moves no mastery (right and wrong); unaided does; review outcome ignores
    hinted right answers; fingerprint/idempotent retry with the hint; export field; Vitest for the hint flag sent.
  - **Not changed**: the public demo (its answers are not evidence); `ActivityEvent` hint telemetry (unused here).
- 2026-10-27: **L4 educators can record a question or an introduction in the Studio.** Investigation (Priority C):
  the only real audience today is the Junior hub (ages 4-9) with one real curriculum, "Maths - Junior"; many 4-6
  year olds don't read, and **0 of its 13 published questions had a recording** - so a pre-reader met "2 mangoes and
  3 mangoes make how many?" as text. The child side already plays a recording when a question appears and on
  "Listen"; the gap was authoring: a recording had to be made in another app and uploaded. Every audio slot in the
  Studio (question read aloud, a choice's sound, an introduction's recording) now has **Record**: the microphone is
  read with the Web Audio API and written as a mono 16-bit 22.05 kHz WAV in the browser (Chrome's own recorder makes
  WebM, which the server refuses; WAV it accepts and every browser plays), listen back, "Use this recording" (an
  ordinary upload - the server's byte checks, storage allowance and library apply), "Record again", discard. Stops
  by itself at 60 s (~2.6 MB of the 5 MB allowed). Refused, missing or failing microphones say so and leave upload
  available; the button is hidden where recording can't work (no getUserMedia, insecure page). The library titles a
  recording after its use and time ("recording of the introduction 27 Oct, 10:19"). "Add a recording" became
  "Upload a recording". EN/KM (Khmer drafted, PD-14).
  - **Browser verified** (isolated stack; the pane has no microphone, so `getUserMedia` was stood in for by a
    generated tone - capture, encoding, upload, server check and playback were real): question prompt recorded,
    listened back, uploaded (201, `audio/wav`, 436 KB for ~10 s), saved on the draft; refused microphone shows its
    message; an introduction recorded, saved, published (v3); as Dara the introduction's **Listen** played the 2.1 s
    recording to the end - the check that could not be done last session. Vitest 392, tsc, eslint clean.
  - **Not verified**: a real microphone and real devices (Android Chrome, iOS Safari) - the owner or an educator
    should record one prompt on a phone. **Tests still needed**: WAV encoder (header, resampling), recorder states and
    failures (Vitest).
  - **Not done, decision for the owner**: device text-to-speech for prompts without a recording (no content language
    tag; Khmer voice quality unproven; some devices send the text online). Research index: `.claude/research/README.md`.
- 2026-10-27: **L5 parent skill page tells the hint story; the builder fixes its own "superseded version" error.**
  - **Parent (Priority D)**: after D-16 the skill page's standing ("3 of 3 answers right", evidence = unaided
    answers) and its per-activity history (all answers, raw from `answer_records`) disagreed without saying why. The
    skill story API now returns `withHint` per activity and `hintedAnswers` for the skill; the page adds "Not counted
    above: n answers given after opening the hint. That's practice, not something shown on their own." and "· n with a
    hint" on history rows. Numbers unchanged (the tests pin them); the meaning is now stated. Pre-V74 answers have no
    hint record and count as before (only 4 published questions had hints then).
  - **Authoring (found during E2E)**: publishing a new version of a question retires the old one, and every activity
    draft still holding it was blocked ("uses a superseded version; use the current published version") while the
    builder labelled the row "Published" and offered no way to switch - the only route was remove, re-add from the
    library, reorder. Item details now carry the question's current published version when the held one was retired;
    the row shows "Version 1 - replaced" (with why) and **Use version 2**, which swaps it in place through the
    existing replace-items call (server checks unchanged, order kept).
  - **Browser verified** (isolated stack): new hinted version of "1 + 1 = ?" published; the activity draft showed the
    replaced badge, one click switched it ("Everything is ready"), v4 published; Dara practised v4 with the hint on
    question 1: stored hinted/unaided correctly, "Recognize addition" evidence 2 -> 3 (unaided only); the skill page
    shows the note and the history row. Also checked: Dara's retired-but-pinned v2 (holding a retired question)
    is still served exactly. **Tested**: SkillStory, ParentInsightTruthfulness 5, LearningJourneyApi 8,
    CrossFamilyAccessSweep; Vitest parent 70 + Studio 74; tsc.
  - **Found, recorded as D-17**: "Secure" after one sitting (see decisions).
  - **Tests still needed**: skill story hint counts; ItemDetail current version for a retired item; builder switch.
- 2026-10-27: **L6 (D-17) "Secure" needs two different days.** Owner chose "two different days" and approved the
  minimal updates to the tests that encoded the old rule. `LearningDays` (new): a child's learning-day zone (the
  family's, V70, else `platform.learning.day-zone`) and the number of distinct days a skill's evidence came from (one
  query per distinct zone; fixed offsets handled explicitly because PostgreSQL reads "+07:00" with the POSIX sign).
  `EvidenceStandard.standingFor` now takes the day count: at the mastery level with evidence from one day -> PROGRESSING.
  Applied everywhere a standing is told: the parent's skills and skill page (`secureAfterAnotherDay` + "all strong so
  far, but all from one day..."), the skill history replay, "what changed" (then and now), the class trend replay, the
  standing history used for school views; an objective is secure only when all its skills are; the "has mastered"
  parent insight needs every skill on two days (else the existing "good progress" sentence).
  - **Tests changed (authorized)**: `ProgressChangeIntegrationTests` and `SchoolAccessIntegrationTests` - the
    "secure" evidence now spans two days (data and comment only, same assertions); `ObjectiveInsightTruthfulnessTests`
    - the new constructor argument, a `LearningDays` mock reporting two days (that test is about evidence strength).
    The golden journey already accepted STRENGTH or PROGRESS - unchanged.
  - **Tested**: 40 suites incl. ProgressChange 2, SchoolAccess 11, ObjectiveInsightTruthfulness 14,
    ParentInsightTruthfulness 5, GoldenLearningJourney, LearningJourneyApi 8, SkillStory, ClassTrend/School*,
    ColdStart, DueReview, AdaptiveEngine, CrossFamilyAccessSweep; Vitest parent 70; tsc. **Browser verified**: Dara's
    "Recognize addition" (all answers today) now reads Progressing with the another-day sentence; history agrees.
  - **Tests still needed**: one-day vs two-day standing; zone boundary (an answer at 23:30 and 00:30 local); fixed
    offset zone; objective and "mastered" gated.
  - **Gap, proposed as D-18**: nothing brings the skill back on another day by itself.
- 2026-10-27: **L7 child screens at phone sizes: the way forward is always on screen.** Walked Dara's real journey
  (path -> "Addition Within 10 Practice - Easy" -> questions -> a wrong answer -> completion -> "Medium" -> leave and
  resume "Hard") at 360x640 and 320x568 (browser emulation, not a device), EN and KM, and the tablet preset.
  - **Found**: on the book's first page the only way on ("Let's go") started at y=654 of a 669 px viewport - a child
    saw the cover, title and question count but not the button (worse with an introduction, and real phones lose
    more height to browser bars); the completion page's "Keep going" was at y=945 of 640. Both had focus but focus
    doesn't scroll there.
  - **Fixed**: below the `sm` breakpoint both action rows stick to the bottom of the screen until the page reaches
    them (a fading paper bar), with "My path" as a round icon button (its name still read out) beside the go button so
    the bar stays one row (fits 320 px, Khmer "តោះទៅ" included). The intro card lost `overflow-hidden` (it stopped
    sticky; the cover clips its own corners). Tablet/desktop unchanged (static row, full labels).
  - **Checked and fine**: home (one big "Continue learning"), question + keypad + Check within 640 px, wrong-answer
    verdict and focused "Next" on screen, path/shelf/buddy without overflow at 320 px in EN and KM, resume returns to
    the next unanswered question. Not a product issue: a 16 px overflow seen only in emulation (a `fixed inset-0`
    background sized to the emulator's desktop scrollbar; phones use overlay scrollbars); screenshots of emulated
    sizes in the browser pane come out cropped, so measurements were used instead.
  - **Verified**: browser (measured positions above), Vitest 392, tsc, eslint. **Not verified**: physical phones.
  - **Tests still needed**: Vitest that the start/keep-going controls are the sticky row's children on small screens.
- 2026-10-27: **L8 the content bottleneck, measured; authors now see each activity's gaps.** Checked against the
  data, not last session's numbers: every installation's real content is the V29 seed (OWNER-GUIDE part 2): 6
  activities x 2 NUMERIC questions, English, all "a + b = ?" on the keypad, 0 pictures, 0 recordings, 0 explanations,
  0 hints, 0 introductions (the 1 hint and 1 introduction on the dev stack are my test edits). The Studio tools for
  every one of those now exist (L1, L2, L4, Y41, X11) - the obstacle is content availability and authorship (D-19),
  then guidance and Khmer review, not missing features.
  - **Built**: the Studio's activities list shows, for each activity's latest version, the help a child gets - "No
    introduction / Teaches first · Read aloud n of m · Explained n of m · Hints n of m" - missing introductions,
    recordings and explanations in the warm ink (7.2:1 contrast), hints neutral (optional by design); one grouped
    query per page (`ActivitySummary.support`). An author scanning a lesson now sees where children meet bare
    questions, instead of opening each activity.
  - **Verified**: browser (admin Studio, the seed's six activities listed with their real gaps), ContentStudio 9,
    ActivityBuilder 3, ContentLifecycleStudio 2, LessonSharing 2, ContentTenancy 9; tsc. **Test still needed**: the
    support counts per latest version.
  - **Not done (decisions)**: D-19 (who authors, languages, review); D-18 still open; no machine speech (see
    `.claude/research/README.md`).
- 2026-10-27: **L9 parent journey on phones; no dead links to skills outside the plan.** Walked the parent's family
  home, Dara's overview, Skills & progress and a skill page at 360x640 (emulation): no overflow (the tab bar
  scrolls sideways by design); every number checked against the data reconciles (12 evidence answers of 14 given,
  2 hinted; "4 of 5" = Easy 1/2 + Medium 2/2 + 1 in the unfinished Hard; Progressing everywhere per D-17).
  **Fixed**: "What changed" (overview and family home) linked every skill to its skill page, but the skill page
  (deliberately) serves only skills in the child's paths - a skill practised elsewhere (a class's own practice, free
  practice of other content) led to "This skill isn't part of this child's learning paths". `SkillChange.onPlan`
  (same source as the skill page); off-plan skills show as text with "practised outside the learning plan".
  Verified in the browser; ProgressChange, LearningJourneyApi, SkillStory suites; Vitest parent 70; tsc.
  **Next**: Studio educator guidance for young learners (D-19 stays open), then parent pages in Khmer at 320 px.
- 2026-10-27: **L10 parent pages in Khmer at 320 px; a writing guide for authors.** Parent family home, overview,
  Skills & progress and a skill page at 320x568 in Khmer: no overflow or clipping; this session's new sentences
  (two-day rule, hint note, outside-the-plan) render in Khmer; only authored content (skill and activity names)
  stays English, as designed. Test parent switched back to English. **Built**: a closed "Writing for young children"
  guide at the top of the activity builder - seven points (teach first, show it, say it, explain after answering,
  hints guide, one idea with familiar things, several questions per skill because 3 answers / two days are needed).
  Craft guidance from the report's strongest findings and the platform's own evidence rules - not curriculum content
  (D-19 stays open). Khmer drafted (PD-14 review). Verified in the browser (renders above "Teach it first" on a
  draft); tsc, eslint, Vitest 394 (the 2 new cases are the catalogue parity checks). Landing page recorded as F1.
- 2026-10-27: **CHECKPOINT - coworker preview (Cloudflare) paused by the owner; product work resumes.** State left
  safe and running (outside the repo, `D:/bk-work/preview`, see docs/COWORKER-PREVIEW.md):
  - **Why the link was stale**: the preview served a backend image and frontend bundle built on 2026-10-21 (nothing
    rebuilt them), and `preview.sh` recorded the bash wrapper's PID instead of cloudflared's, so `stop` left the
    tunnel running (pointing at a stopped or stale stack) and `status` said "not running".
  - **Done**: rebuilt from `6ab3005` (backend from `git archive`, so the uncommitted owner edit is not shipped;
    frontend bundle `index-C11XCiG2.js`); preview DB migrated to V74; **the original link was kept** (its hostname
    recovered from cloudflared's metrics endpoint; the backend's allowed origin / public URL point at it); a
    duplicate tunnel I had started was stopped. `preview.sh`: new `update` command (rebuild + swap backend, keeps
    gate and tunnel/URL), PID fix (records cloudflared itself; `stop` kills the tree and any orphan of ours), help
    text; backup `preview.sh.bak-2026-10-27`; rollback image `bk-rr-backend:before-6ab3005`, bundle
    `rr/dist-before-6ab3005`. Kit checks updated (`browser-check.mjs` heading) and added (`journey-check.mjs`).
  - **Verified through the original link**: 17/17 anonymous probes refused (401); all five roles sign in (parent ->
    child space, Khmer and back; teacher; both principals; admin); served bundle = local build; a child answers a
    question ("That's right!", 0 server errors).
  - **Unfinished**: final pass after the last product commits (run `preview.sh update`, then the two checks);
    `journey-check.mjs` should click "Finish" so it doesn't leave an activity fully answered but unfinished.
  - **Risks**: Quick Tunnels have no uptime guarantee - if cloudflared exits, the URL is gone (a new one must be
    sent); the gate password is unchanged; the admin preview account's language was left in Khmer by an earlier run.
- 2026-10-27: **L11 parents learn the two-day rule and how to help at home.** "How we describe progress" now says
  why strong answers can still read "progressing" (D-17: secure needs strong answers from two different days).
  The child's overview has a short "Helping at home" card under the insights: sit with the youngest and read the
  questions aloud together, praise effort and method, practise a skill that needs it with real things, stop at the
  end of an activity. General guidance from the report (co-use, process praise [Promising]; concrete before
  symbolic [Strong]; ending on purpose [Promising]) - not curriculum; a per-skill home activity, as the report
  suggests, would be educator content (D-19). EN/KM (Khmer drafted, PD-14). Verified in the browser at 360 px (fits);
  tsc, eslint, Vitest 396. Also checked: an activity fully answered but not finished is completed on return (designed
  behaviour, `finishAttempt`) - the preview journey script's failure there was the script's assumption.
- 2026-10-27: **CHECKPOINT - preview final update blocked by a permission prompt (owner action needed).** The
  original link (`room-convenience-balloon-rights.trycloudflare.com`, gated) serves the `6ab3005` build, verified
  through it after the 14:28 `preview.sh update` (17/17 anonymous probes refused; all five roles; bundle = local build;
  a child answers a question, 0 server errors). Since then only `5160cde` (L11: parent explainer sentence and
  "Helping at home" card - frontend only) is newer. Running `preview.sh update` again was refused by Claude Code's
  auto-mode classifier as a production deploy; nothing ran (no `build-5160cde` artefacts). **To finish** (owner, or
  with permission): `bash /d/bk-work/preview/preview.sh update`, then from `D:/bk-work/preview`:
  `node browser-check.mjs "$(cat tunnel.url)"` and `node journey-check.mjs "$(cat tunnel.url)"` (with
  `PLAYWRIGHT_BROWSERS_PATH=D:/bk-work/pw-browsers`). The journey check now finishes an activity it completes.
- 2026-10-27: **M13 role journeys on phones; F1a every word of the public pages editable.**
  - **M13**: at 360x640 (emulation) every page of the teacher (classes, a class, Studio, media, account), the
    principal (overview, classes, teachers, plan, account) and the administrator (20 pages incl. the Studio activities
    list and the activity builder) - no overflow or load errors; Khmer pass on the admin dashboard, activities list and
    builder (this session's Studio texts render in Khmer). Child and parent were covered in L7/L9/L10.
  - **F1a**: V75 `site_copy` (namespace + entry key, EN and/or KM, version, who changed it); the frontend's message
    catalogs stay the defaults and administrators' words are laid over them (`setCopyOverrides` in i18n/catalog; an
    entry that takes values uses {1}, {2}...). Only the three public catalogs (landing, trust, try) are accepted
    (service and DB check); plain text up to 600 characters, control characters refused; optimistic version check;
    every change and reset audited. Public `GET /api/v1/site/copy` (read-only, anonymous), admin
    `GET/PUT/DELETE /api/v1/admin/site-copy`. Operations -> Landing page has "Words on the public pages": all 188
    entries by page and section, searchable in both languages, original shown beside yours, reset to the original;
    loaded on demand. The public pages fetch the overrides once per visit and keep their defaults if that fails.
  - **Browser verified**: changed the hero headline and the footer line ("{1}" -> 2026) in English; the landing page
    showed both; Khmer visitors still saw the original Khmer; reset both -> originals back, the public list empty;
    four audit events. Anonymous admin endpoint 401. **Tested**: PublicDemoAndStatements 4, ServerTextCoverage 3;
    Vitest 398, tsc, eslint. **Tests still needed**: site copy service (namespace/key/length/version/audit), overlay
    (string/template/locale fallback), the editor panel.
  - **Integrated the owner's 2026-10-27 directions** as F1b-F1d and F2 in the status table (no new plan).
- 2026-10-27: **Sequencing after F1a, and M13 documents.** Next in the execution order is M11 (blocked: the S3
  provider needs D-12's library approval and an abstraction with one implementation would be speculative) then M12
  (needs the owner's school-record/consent model - recorded in the status table). M13's documents done: the canonical
  progress record gets a dated current-state pointer and a current "Immediate next work"; `docs/COWORKER-PREVIEW.md`
  documents `preview.sh update` (keeps the link) and the PID fix. **Next product item: F1b** - the highest-priority
  unblocked children-and-parents work. Existing base (Try page, M2): real lessons played exactly as a child plays them,
  server-checked, saved nowhere, ending with "Create a family account". Gaps to build: the activity's "Learn first"
  introduction and the Learning Buddy at the start, a fuller completion moment, an explicit "Not now", and a short
  "what an account gives you".
- 2026-10-27: **F1b the public demo teaches first and ends without pressure.** On the Try page (M2: real lessons
  played as a child plays them, server-checked, saved nowhere): an activity with an introduction opens with it - the
  buddy, the "Let's learn" card (picture, text, Listen) and "Let's go" (pinned on phones, as in L7) - and "Show me
  again" stays available during the questions. The ending keeps "That's how a lesson goes" and adds "With a family
  account": the child's answers are kept and each next lesson is chosen from them; you set up each child (subjects, a
  daily time limit if wanted, their own space and buddy) - only what the product does, no price or trial promised; then
  "Create a family account", "Try another lesson", and "Not now" (back to exploring, nothing asked). All new words are
  editable through F1a.
  - **Defect fixed (found while testing)**: publishing a newer version retires the one chosen for the demo, which then
    silently left the public page while the admin picker still showed it chosen (the dev demo showed 1 lesson of 2).
    Candidates now carry `shown`; a chosen but no-longer-shown version reads "Not shown to visitors any more: a newer
    version replaced it, or it was withdrawn. Choose the current version instead."
  - **Browser verified** (anonymous visitor, 360x640): teach-first page (card, Listen, focus on Let's go, Let's go at
    572-624 of 640, no overflow), Show me again, both questions answered, completion with the account points, Create a
    family account and Not now; learner records unchanged (answers 1442, attempts 688, evidence 1440 before and after).
    Admin picker: the note on a replaced version (recreated in the dev DB for the check), removed through the picker.
    **Tested**: PublicDemoAndStatements 4, ServerTextCoverage 3, Vitest 398, tsc, eslint. **Tests still needed**:
    try page intro phase and ending; candidate `shown`.
  - **Next**: F1c (parent demo with sample data) or, in roadmap order, whatever the owner unblocks among M11/M12.
- 2026-10-27: **CHECKPOINT - coworker preview, end of this usage cycle.** The original link
  (`room-convenience-balloon-rights.trycloudflare.com`, gate and password unchanged) serves **`c0206b7`** (L1-L11):
  `preview.sh update` ran at 14:57 - not by Claude (its attempts were refused by the auto-mode classifier as a
  production deploy, and left no artefacts); the build log, `src-c0206b7`, the bundle and the backend container all
  date from 14:57. Verified through the link afterwards: 17/17 anonymous probes refused, all five roles sign in, the
  served bundle is that build, a child answers ("You got it!", 0 server errors). **Not live**: `8681bf8` (F1a editable
  public words, V75; F1b demo teach-first and ending; demo picker note) - CI green. **To finish**: run
  `bash /d/bk-work/preview/preview.sh update`, then `browser-check.mjs` and `journey-check.mjs` against
  `$(cat tunnel.url)` from `D:/bk-work/preview` (with `PLAYWRIGHT_BROWSERS_PATH=D:/bk-work/pw-browsers`).
- 2026-10-27: **Coworker preview updated - the original link serves `8681bf8`'s code (built from `1e90cbf`, which only
  adds the roadmap).** The owner approved `preview.sh update` through the permission prompt; the tunnel, URL, gate and
  password were kept. Verified through `room-convenience-balloon-rights.trycloudflare.com`: gate holds (anonymous 401,
  re-checked by the update); all five roles sign in (parent -> child space, Khmer and back; teacher; both principals;
  admin); served bundle `index-B22l6RN7.js` = the new build; a child answers and finishes an activity ("That's
  right!", completion screen, 0 server errors); preview DB at V75; F1a's new public endpoint answers 200 through the
  gate and 401 without it.
- 2026-10-27: **F2a what the product really collects - the basis for any policy.** Chosen first because F1c's sample
  data and F1d's landing copy may only claim what this confirms. Legal plumbing already exists (Y15): the privacy
  notice and terms are external links with a version that every account agrees to at registration (asked again on
  change), shown only once the owner configures them - no placeholder. Inventory from the code and schema: accounts
  (email, password hash, optional verified phone, admin authenticator secret); children have no account - a display
  name and date of birth; every answer incl. hint use, evidence, mastery, reviews, insights; sessions keep IP and user
  agent, action tokens the requesting IP; caregiver invites (email); payments (no card/bank data); educator media;
  audit events by id. **No cookies, no analytics or third-party scripts; fonts bundled (@fontsource)**; localStorage
  keys listed. Third parties only once configured: email (SMTP), SMS (none, D-04), hosting. Export and deletion with a
  7-day grace (max 90) and a sweep that erases everything but id-only audit events.
  - **Findings for the owner**: (1) expired sessions/used tokens with IP and user agent are never cleaned up -
    recommended rule recorded (keeps Battle 4A reuse detection); (2) the per-child consent model (`ConsentService`) is
    built but not wired - today the parent's account-level agreement covers children; (3) nothing is agreed at all
    until the owner publishes texts and sets `LEGAL_*`. Ten questions for qualified review are in the file (Cambodia
    PDP draft, parental consent, ages 3-12, retention, backups, schools' role, payments, educator licence, hosting
    transfer, governing language). **Not legal advice; no compliance claimed.**
- 2026-10-27: **F1c what a parent sees, before registering.** New public page `/try/parent` ("Parents: see what you'd
  see", linked under the Try page's lead): a fictional sample child, Mealea (6), inside a dashed "Sample" frame, with
  the lead "Mealea is not a real child, and nothing here is saved". Built from the parent area's own pieces - the
  standing marker and evidence line (L-series), the four "Helping at home" ideas (L11) - plus recent activity, "What
  we can say" with its basis, and "What comes next, and why"; ends with "Create a family account", "Try a lesson
  first" and "Not now". **Static and client-side**: no API call, no learner record, no mastery touched. The sample
  numbers follow the server's own rules (EvidenceStandard): three answers before any judgement, SUFFICIENT below the
  saturation count of 10, "Secure" only across two days (D-17: "5 of 6 right, all today" reads Progressing), hinted
  answers left out of the evidence (D-16: "1 of 3 right on her own" plus one hinted right answer), and the insight
  names only what the standings support. Every word is editable through F1a: catalog `features/marketing/parentDemo`
  added to `SiteCopyService.NAMESPACES` and to the DB check (**V76**, constraint replaced, no data change) and shown in
  the editor as "What parents see (sample)". "Not now" on the Try and parent-demo pages got a 44px touch target.
  - **Runtime verified (dev stack, 8092 + Vite)**: V76 applied; `/try/parent` anonymous at 360px - all sections, no
    horizontal overflow, CTA links to /register, /try, /; the Try page shows the link. An administrator's English
    override of the title (admin API) appeared on the page; Khmer visitors still saw the Khmer original; a namespace
    outside the public pages is refused (400); reset (204) emptied the public list; SITE_COPY_CHANGED and
    SITE_COPY_RESET audited. **Tested**: PublicDemoAndStatements 4, ServerTextCoverage 3; Vitest 400; tsc; eslint.
  - **Not verified**: the editor panel's new group in a browser (type-checked only); a screenshot (the pane was
    hidden - checked through the page text and layout measurements). Khmer copy is Claude-drafted (PD-14 review).
  - **Tests still needed**: the parent demo page (renders the sample label and no request), the V76 namespace in the
    site copy service tests listed under F1a.
  - **Next**: F1d landing redesign, then F2b drafts.
- 2026-10-27: **F1d-1 the landing page shows the learning loop instead of describing it.** Research used as evidence:
  motion must explain, not decorate (seductive details reduce learning, Sundararajan & Adesope 2020 [Strong]);
  marketing establishes features, not effectiveness (no outcome claims). "How it works" replaced its two screenshots
  and the static five-step list with one demonstration: a child answers 3 + 2 (the buddy cheers, "That's right!"),
  the answer is "kept as evidence for Add within 10", the parent's skill row lights up, the third answer turns "Too
  early to tell / partial evidence" into "Progressing / sufficient evidence" with "Secure comes once your child shows it
  again on another day" (D-17), and "Why this next?" gives the app's own CHALLENGE reason. Built from the real
  `Standing`, `EvidenceLine` and `LearningBuddy`; the five loop sentences are its captions and step buttons
  (`aria-current="step"`). Plays only while on screen, pausable (WCAG 2.2.2); under reduced motion nothing plays and the
  whole story is shown, each step still selectable; lines not yet shown are hidden from screen readers. All words in
  the landing catalog (editable through F1a); unused keys removed (two shot captions, `progressing`, `enoughToJudge`)
  and the sample stats corrected to fit the rules ("3 of 3 right · all today", was "4 of 6" beside a "consistently
  correct" reason). The two screenshots it replaced are deleted (no other reference; the capture script can remake
  them).
  - **Browser verified (headless Chromium via Playwright against the dev server)**: at 1280x900 and 360x740 the steps
    advance 1->5 in ~12 s, the parent row reaches "Progressing", Pause holds the step, a step button jumps to it, no
    page errors, no horizontal overflow; reduced motion: starts on step 5, does not advance, button reads "Play";
    Khmer at 360px renders with no overflow. Screenshots reviewed (phone: buddy moved above the question after it
    overlapped). **Tested**: Vitest 400, tsc, eslint.
  - **Not verified**: a screen reader; a physical phone.
- 2026-10-27: **F1d-2 the last fixed words on the landing page, and a way to the parent demo.** The closing headline
  and the footer line came from the app-wide `common.tagline`, so they could not be changed in Operations -> Landing
  page; they are now `promises.closingTitle` and `nav.tagline` in the landing catalog (same words by default). "For
  parents" ends with "See a sample parent view" -> `/try/parent` (F1c was reachable only from the Try page).
  - **Deliberately not editable from the landing editor**: the product's own vocabulary shown on public pages - the
    buddies' names and characters, standing and evidence labels, "Helping at home", language names, the legal links.
    Changing them there would make the public pages disagree with what families find inside; they change with the
    product.
  - **Runtime verified**: admin override of `promises.closingTitle` showed as the closing headline (headless Chromium),
    reset restored the original; footer line and the new link render. **Tested**: Vitest 400, tsc, eslint.
- 2026-10-27: **F2b draft privacy notice and terms, from the inventory - not published.**
  `docs/legal/PRIVACY-NOTICE-DRAFT.md` (who we are, what is collected about adults / children / learning / devices,
  why, cookies and storage, who can see it, keeping, choices, children, changes, contact) and `docs/legal/TERMS-DRAFT.md`
  (what the product is, accounts for adults, families - nothing to pay in early access, schools - arranged with the
  team and family-granted access, lessons and educator uploads, what progress means - not an assessment or a promised
  result, acceptable use, availability, ending, liability and law). Every statement traced to the code or the public
  page; `[OWNER: ...]` for operator identity, contacts, providers, retention and two commitments to confirm;
  `[REVIEW n]` tied to the inventory's ten lawyer questions. No compliance claimed; English only until a native legal
  translation (question 10). Publishing stays the owner's step: host the reviewed texts, set `LEGAL_*` (Y15).
  - **Checked against the code while writing**: no child age range is enforced (date of birth must not be in the
    future) - so the drafts don't state one; sign-out removes the refresh token; a child's sitting lives in
    **sessionStorage** (the inventory said localStorage - corrected); "Sign out everywhere", family-revocable school
    access, child detail edits and account suspension exist.
- 2026-10-27: **F1d-3 "For parents" corrected to what the product does (found in a full-page phone review).**
  - **Defect: the evidence ladder used standing words.** The four line weights were titled "Not started / Too early to
    tell / Growing / Secure", while inside the app the same lines read "No evidence yet / Partial / Sufficient / Strong
    evidence" and a strong line can sit beside "Needs practice" - a parent taught by the landing page would misread
    their child's page. The titles now come from the app's own `evidenceLabel` (product vocabulary, as decided in
    F1d-2); the explanations stay editable and now match EvidenceStandard: one or two answers partial, three or more
    sufficient, "many answers" strong (the saturation count is configurable, so no number is promised).
  - **Defect: the lead contradicted the rules** ("Three answers are not a verdict" - the product judges from the third
    answer). Now: one right answer is not a verdict; how it is going from the third answer; secure only once shown on
    two different days (D-17).
  - **Defect: the "next step" rules didn't match AdaptiveEngine.** "Evidence mixed or still thin -> more practice" is
    not what happens: below the evidence minimum or between the thresholds the path continues as planned
    (`Direction.CONTINUE`); reinforcement (mostly wrong answers) practises the skill's first prerequisite or, without
    one, the skill itself. Rules rewritten to: mostly wrong -> the skill it builds on or more of the skill; too few or
    mixed -> the planned path continues; consistently right -> harder activity; recent answers going wrong -> Smart
    Review. Khmer updated alongside (Claude-drafted, PD-14).
  - The How-it-works step list is more compact on phones.
  - **For the owner (not changed - a claim only the owner can make)**: the hero says "lessons for children aged 3 to
    12" / "Short lessons for ages 3 to 12", while the only real content is Junior maths (4-9) and no age range is
    enforced. Editable in Operations -> Landing page if the owner wants it narrower.
  - **Browser verified (headless Chromium, 360px, EN and KM)**: labels read as in the app, no overflow; screenshot
    reviewed. **Tested**: Vitest 400, tsc, eslint.
- 2026-10-27: **F1 public journey joined up, and checked for accessibility.** The Try page's ending ("That's how a
  lesson goes") now also offers "Parents: see what you'd see" -> `/try/parent`, so a visitor who just played a lesson
  can see the parent side of those answers; both parent links on the Try page have 44px targets. The rest of the
  landing page was read for accuracy against the code: break reminders (10/15/20/30/45 min, V60 check), the grown-up
  gate, no ads/chat/rankings, export and deletion, family-revocable school access - all true as written.
  - **Browser verified (headless Chromium against the dev stack)**: played "Addition Within 5 Practice" on /try at
    360px to the ending, followed the new link, the parent demo loaded; **axe (WCAG 2.0-2.2 A/AA tags, as the E2E
    suite uses)**: no violations on / (1280 and 360), /try (360), /try/parent (360 and 1280). **Tested**: Vitest,
    tsc, eslint.
- 2026-10-27: **F1d-4 the buddy choice, tried on the landing page.** In "A world of their own" the five buddies are now
  buttons (`aria-pressed`): tapping one plays the child app's own moment - the buddy's cheer and its words ("Domrey
  is your buddy now!", from the buddy catalog, so it reads exactly as inside) in a polite status line; nothing is
  saved. One new editable line introduces it ("Tap a buddy: this is the choice a child makes in their own space.
  Nothing here is saved."). Motion is the existing cheer, which stops under reduced motion (index.css).
  - **Browser verified (headless Chromium)**: EN at 360px and KM at 1280px - choosing Domrey sets only its
    `aria-pressed`, the status reads the product's sentence, no overflow, axe no violations; screenshot reviewed.
    **Tested**: Vitest 400, tsc, eslint.
- 2026-10-27: **F1d-5 the demonstration names its step on phones.** Below 640px the five step captions sit under both
  panels, so the step being played was out of sight; the current step now stays under the site header while the
  panels scroll (`aria-hidden` - the step list remains the accessible version). **Browser verified (headless Chromium,
  360x740)**: steps 1->5, pause, manual step, no errors, no overflow; screenshot shows step 5's sentence pinned above
  the child panel. Khmer drafted this session listed for native review in `KHMER-REVIEW-Y33.md` (PD-14); the Khmer
  parent demo re-checked at 360px (no overflow, axe clean).
- 2026-10-27: **CHECKPOINT - coworker preview updated to `093e4da` (code of `ba0ea1c`, CI green on all four jobs);
  the link is the owner's new one after the laptop restart: `virtual-remain-framework-notre.trycloudflare.com`.**
  Before: the link served `index-B22l6RN7.js` (the `1e90cbf` build) with no `/try/parent` route - F1c, F1d and V76 were
  not live. `preview.sh update` (run as is; the existing tunnel, URL, gate and password kept; the script re-checked the
  gate locally and through the URL). **Verified through the link**: anonymous 401; `browser-check.mjs` - all five roles
  sign in (parent: family home, Sophea's progress, child space, Khmer and back; teacher class + Studio; both
  principals; administrator payments page); `journey-check.mjs` - served bundle = the new local build
  (`index--FWIlrRV.js`), "Let's go" fully on screen at 360x640, the child answered "7 + 3 = ?" -> "You got it!", 0
  server errors (this run answered one question mid-activity; the activity is left resumable, it was not finished);
  this session's pages through the gate - `/try/parent` with the sample child, the How-it-works demonstration
  advancing on its own (step 3 after ~6 s), the For-parents link, public site copy 200, no 5xx; preview DB at **V76**
  (read-only query of `flyway_schema_history`).
  - **Next, in order**: owner decisions (ages claim on the hero, `[OWNER]` items in the legal drafts, the inventory's
    ten lawyer questions, D-18, D-19, M11/M12 inputs); native Khmer review of the F1c/F1d text (KHMER-REVIEW-Y33.md);
    further F1d only where the landing page still disagrees with the product.
- 2026-10-27: **Business requirements document; the 3-12 audience kept as decided.** New
  [`docs/BUSINESS-REQUIREMENTS.md`](docs/BUSINESS-REQUIREMENTS.md): purpose, customers and business model as
  implemented, the audience vs curriculum age range vs available content, roles and permissions, a feature inventory
  with status labels (Live / Built, limited / Partial / Planned / Blocked / Undecided), end-to-end workflows, the
  learning model with the server's actual thresholds (evidence 3 / saturation 10; standing 0.6 / 0.8 + two days;
  adaptive 0.4 with 2 answers / 0.8 with 3; Smart Review below 0.5 with 2), business rules, integrations and
  operations, open decisions and known gaps. Facts taken from the code and the records (constants read from
  `EvidenceStandard`, `AdaptiveEngine`, `SmartReviewService`, `application.yaml`, migrations), not from intentions;
  it links to the roadmap, progress record, product brief and data inventory instead of repeating them.
  - **Owner decision recorded (D-09)**: ages 3-12 is the platform's intended audience. My earlier question about
    narrowing the hero is withdrawn. Public copy now separates the two: the proof line says "Made for ages 3 to 12"
    (was "Short lessons for ages 3 to 12", which read as lessons existing across the range) and the status line says
    "Early maths (ages 4–9) today · Khmer and English lessons in preparation". Both stay editable (F1a).
- 2026-10-27: **Registration says what a family is asked for.** With "I'm a parent" selected, the form now says: email
  and a password, a family name optional; each child is added afterwards with the name they're called in the app and
  their date of birth; no payment details - true to `docs/legal/DATA-INVENTORY.md`, and nothing is asked about a child
  before registering. **Browser verified (headless Chromium, 360px, EN and KM)**: note shown, no overflow, axe clean;
  the hero's new audience/availability lines render in both languages. **Tested**: Vitest 400, tsc, eslint. Khmer
  added to the review list below (PD-14): `features/auth/messages.ts` `register.parentNext`; `trust.proof.ages`,
  `hero.status`.
- 2026-10-27: **Finding - two order-dependent backend tests (test defect, not a product regression).** CI on
  `e7952d0` (frontend copy and docs only; backend identical to the green `ba0ea1c`) failed in
  `DueReviewIntegrationTests.aDueReviewReachesAChildWhoHasFinishedEveryStep:57` ("a skill this curriculum doesn't
  teach": expected 1 row, 0) and `SubscriptionIntegrationTests.aFamilyStopsAJourneyToChangeSubjectAndChoosingItAgain
  CarriesOnWhereItStopped:129` ("two subjects to choose between": >= 2 curricula). A fresh database holds only the V29
  seed (one subject, one curriculum), so both pass only when another test class has created more content first -
  and Surefire's class order follows the runner's file-system order, which differs between CI machines. **Not fixed:
  test files may not be changed without the owner's authorisation.** Proposed fix (needs that authorisation): each
  test creates the extra skill / second published curriculum it needs in its own setup. Failed jobs re-run to
  confirm.
- 2026-10-28: **Resumed; the CI failure on `e7952d0` was the test-order problem, confirmed.** Re-running the failed
  job on the same commit (identical code) passed all four jobs - a regression would fail every time. The two tests
  (recorded 2026-10-27) stay unchanged pending authorisation. The coworker preview was **down** at resume (Docker
  Desktop not running, no `tunnel.url` - the laptop was restarted); left for the reserved phase.
- 2026-10-28: **A family with a child of an age that has no path yet is told so while adding the child.** Walked the
  real journey on the dev stack (new parent, children aged 3, 6 and 11; only Junior 4-9 content exists): the child
  page already said "No path is for Three's age yet", but step 2 of "Add a child" jumped straight to "160 paths for
  other ages" with no explanation. It now first says: nothing is made for the child's age yet; a path for other ages
  can be chosen, or decided later and added from the learning plan once one is published (D-09: the audience is 3-12
  while the content grows). A child with no path who opens their space is met by the buddy: "A grown-up needs to choose
  what you'll learn first" - already right, unchanged. **Browser verified (headless Chromium, 390px)**: ages 3 and 11
  show the note, age 6 shows "Made for Six's age" as before, no 5xx. **Tested**: parent Vitest 70, tsc, eslint.
  Khmer `subjects.noneForAge` drafted (PD-14). (The dev database holds ~160 E2E-made curricula - test data, not shown
  on a real installation.)
- 2026-10-28: **Defect fixed: a lesson open in two tabs could show a child their own answer beside the other tab's
  verdict.** Found by testing what the team guide asks testers to try (two tabs on one lesson). The server is right:
  the second answer to an already-answered question gets 409 and is not recorded, and the screen recovers by fetching
  the recorded answer - but it showed the recorded **verdict** with the answer typed **in this tab**: tab A answers
  "1 + 1" with 2, tab B then types 3 and saw "3 ... You got it!" - feedback that contradicts the child's own answer.
  Fix at the contract: the answer response (`AnswerRecordResponse`) now carries `submittedAnswer`, exactly as recorded
  (additive; the list endpoint stays behind `requireLearner` for the attempt's child), and the recovery shows that
  answer with its verdict. Evidence was never affected (the stale answer was refused).
  - **Runtime + browser verified (dev stack, headless Chromium, 390px)**: tab A answered 2, tab B typed 3 ->
    409 for B's answer, B shows **2** and "You got it!" (screenshot reviewed). **Tested**: backend unit + 12
    integration classes that call the answers endpoint (45 tests, incl. the cross-family access sweep) and 3 more,
    all green; frontend Vitest full suite, tsc, eslint.
  - **Test still needed** (test files frozen): the recovery path showing the recorded answer, not the local one.
- 2026-10-28: **A long child name no longer breaks the family pages on a phone.** The server accepts names up to 80
  characters; one typed without spaces (Latin script) pushed "Add a child", the family home, the child page and the
  child's space up to ~1,000 px wider than a 360 px screen. Causes and fixes, smallest first: no word-break fallback
  anywhere -> `overflow-wrap: break-word` on `body` (only words longer than their line break; nothing is resized);
  flex items holding the name sized by its longest word -> `min-w-0` + `overflow-wrap: anywhere` on the name heading,
  the "Start ...'s session/learning" buttons, the breadcrumb and the shared `SectionTitle`; the parent page grids had
  no column below desktop width, so their implicit column grew to the word -> `grid-cols-1` (`minmax(0,1fr)`) on the
  family home, child, skill and account grids.
  - **Browser verified (headless Chromium, 360px)**: an 80-character name across add-a-child steps 2-3, the child
    page, the family home and the child's space: 0 px horizontal overflow (was 291-1,080 px); the name wraps
    (screenshot reviewed). Regression: the test family's home and child page at 1280 and 360 px - no overflow, axe
    clean, desktop two-column layout unchanged (screenshot). **Tested**: Vitest full suite, tsc, eslint.
  - **Not checked**: the same edge case on teacher, principal and admin pages (class and school names).
  - **Same guard on the other page grids** that hold typed names or admin-edited words (admin operations, schools,
    payments, learning, media library, the teacher's skill follow-up, the landing page, the demonstration, add a
    child): `grid-cols-1` below their breakpoint. No visual change for normal content - verified: admin overview,
    payments, schools, learning and the teacher home at 1280 and 360 px with 0 overflow; landing, Try and parent demo
    axe clean; the demonstration's play/pause/step checks unchanged. Long names on school pages themselves were not
    exercised (needs a family to join a class).
- 2026-10-28: **Network failures while a child answers - checked, behaving correctly (no change).** On the dev stack
  with headless Chromium (390px), the seed child mid-lesson: (1) **reply lost** (the answer reached the server, the
  connection dropped before the reply): the screen recovered from the recorded answer - "2 + 1" with 3, "That's
  right!" - and the database holds that answer **once**; (2) **never sent** (offline at "Check"): "We couldn't send
  that answer. Please try again.", the typed answer kept, "Check" again recorded it ("3 + 4" with 7) - **once**. Also:
  one confirmation email per registration (a duplicate count earlier came from the local mail catcher's search).
- 2026-10-28: **F1d-6 the landing page says each thing once.** Right after the hero, "For families / For schools"
  (two cards, ~1,150 px on a phone) repeated what "For parents", "A world of their own", "Plans" and "Schools" each
  say in full further down, and the hero already offers both ways in ("Start as a family", "For schools" -> the
  Schools section). Removed, with its 18 catalog entries (EN + KM) so the words editor lists only words that appear;
  nothing linked to it. Subject cards' picture band is shorter on phones (h-32, h-44 from 640 px). Phone page height
  15,951 -> 14,932 px. **Browser verified (headless Chromium)**: section order and heights measured at 360 px; axe
  clean at 1280 and 360. **Tested**: Vitest 400, tsc, eslint.
- 2026-10-28: **Closed F1c's "not verified" item: the words editor in a browser.** As the administrator (dev stack,
  headless Chromium, 1280px): Operations -> Landing page -> "Show the words" lists four groups - Landing page, Trust
  and safety, Try a lesson, **What parents see (sample)** - 212 entries after F1d-6; searching "Mealea" finds the parent
  demo's 4 entries (`lead`, `childName`, `childLine`, `insight`); axe clean. Also: `LAUNCH-CHECKLIST.md` reviewed
  against this program (legal drafts, administrators' authenticator, payment instructions, Khmer review list,
  retention of sign-in records, PDP status; PD-29 is built).
- 2026-10-28: **"Why this next?" says when an evidence-based decision was made.** On the test family's page a skill
  read "Progressing" beside "Smart Review: recent answers on Recognize addition were mostly incorrect" - both true
  (the review was decided before later right answers lifted the skill), but without a time it reads as a
  contradiction. The server already sent `explanation.decidedAt`; it was never shown. Reinforce, prerequisite,
  challenge and review reasons now end with "Decided today / yesterday / Oct 20." (curriculum and teacher-assigned
  steps unchanged - a test pins the latter's text). Also fixed: with no preceding activity the sentence began in lower
  case ("answers on ... were consistently correct").
  - **Checked**: `parentReason` run directly with sample steps (vite-node against the real module and catalogs): review
    with no date unchanged; "Decided yesterday."; challenge capitalised and "Decided Oct 20."; curriculum and teacher
    texts unchanged. **Tested**: Vitest 400, tsc, eslint. **Not browser verified**: none of the test family's children
    currently has a review/adaptive step up next. Khmer `parent.decided` drafted (PD-14). Note: running that check made
    `npx` fetch `vite-node@6.0.0` from the npm registry into the npm cache (not the project) - unplanned; later runs used
    `--no-install`.
- 2026-10-28: **CHECKPOINT - coworker preview updated to `7705de8` (CI green on all four jobs) on the owner's
  current link `immediate-marketplace-complexity-optimal.trycloudflare.com`.** At resume the preview was down (Docker
  not running); the owner brought the stack and a new tunnel back at 09:52; it served yesterday's `093e4da` build
  (`index--FWIlrRV.js`) - last session's interrupted update had not replaced it. `preview.sh update` run as is (tunnel,
  URL, gate and password kept; the script re-checked the gate through the URL). **Verified through the link**:
  anonymous 401; `browser-check.mjs` - all five roles sign in (parent family home, Sophea's progress, child space,
  Khmer and back; teacher class + Studio; both principals; administrator payments); `journey-check.mjs` - served
  bundle = the new build (`index-DeqW-Tb0.js`), the child resumed an activity, answered "8 + 2 = ?" with 10 ->
  "That's right!", **finished it** (completion screen), 0 server errors; this session's changes - "Made for ages 3
  to 12", "Early maths (ages 4–9) today", the old family/school cards gone, registration's "No payment details"
  note, `/try/parent` - 0 overflow at 360 px, no 5xx; preview DB at **V76**.
- 2026-10-28: **Long names on the school side and the rest of the family settings.** Closed the "not checked" item:
  a new family (email confirmed) added a child with an 80-character name, joined the seed class Grade 1 A with its
  code, and the teacher opened the class and the child. Overflow at 360-390 px: teacher's student page **906 px**
  (header flex item), the parent's "School & settings" tab **375 px** (the "Open ...'s space" button, then the
  caregiver access options and "Delete ..."); the class table stays inside its own scroll area (page 0). Same fix as
  before (`min-w-0` + `overflow-wrap: anywhere` on the item holding the name); the caregiver "helping" page header
  too. After: 0 px on all three. **Tested**: Vitest full suite, tsc, eslint.
- 2026-10-28: **Another family's child, by URL: private, and now without a pointless "Try again".** As the seed
  parent, opening another family's child (`/parent/children/<id>`, `/learn/<id>`, a skill page under it): the server
  answers **404** (it doesn't confirm the child exists), the pages say "We couldn't find this child in your family" /
  "This space isn't open right now"; an unknown address shows "Page not found". The shared `ErrorState` offered "Try
  again" even for 403/404, where retrying can't change the answer - now hidden for those two (other failures keep it);
  the breadcrumb is the way back. **Browser verified** (dev stack, 390px); **Tested**: Vitest 400, tsc, eslint.
- 2026-10-28: **Keyboard check of the landing page's interactive parts (no change needed).** Tabbing through the
  page reaches the five demonstration steps, Pause and the five buddies in order, each with the global 2px focus ring.
  BRD gains a glossary of the product's terms (section 10).
- 2026-10-28: **New principal on a phone - two copy fixes.** Walked register -> confirm -> set up the school -> plan
  at 360 px (0 px overflow throughout, a 50-character school name wraps). With "I'm a principal" chosen, the sign-up
  page still said "Start your family's learning journey" - it now says "Bring your school's classes to Bookeyond Kid".
  The plan screen said "once its payment is confirmed" three times in a row; the plan picker's lead now only says
  who sets prices (the shared "hasn't paid" status line stays - the admin view uses it too). **Browser verified**;
  **Tested**: Vitest 400, tsc, eslint. Khmer `register.principalSubtitle` drafted (PD-14).
- 2026-10-28: **Invited teacher on a phone - the sign-in page spoke only to parents.** Walked the paid seed school's
  principal inviting a new teacher -> the invitation email ("You're invited to teach at ...") -> "Set up your account"
  -> "Your account is ready" -> sign in -> the invitation on the teacher's home -> Accept -> "You've joined ..." (0 px
  overflow at 360 px throughout). The sign-in page, used by every role, said "Log in to continue your child's learning
  journey" - now "Log in to your Bookeyond Kid account - families, teachers and schools alike." **Browser verified**;
  **Tested**: Vitest 400, tsc. Khmer `login.subtitle` drafted (PD-14).
  - Also: after accepting, the teacher's notice repeated the empty state below it ("Your classes will appear here once
    the principal assigns you"); the notice is now just "You've joined <school>." (the E2E check `joined <school>` still
    matches). Swept all-role surfaces for parent-only wording: sign-in fixed above; register, reset, verify and the
    account emails are role-neutral. **Tested**: Vitest 400, tsc.
- 2026-10-28: **Caregiver journey; a helper's home no longer tells them twice to add a child.** Walked M10b on the
  dev stack (390px): a parent invites an address with no account -> the email ("You're invited to follow Mealea's
  learning") says to sign in, or create an account with that address and confirm it -> the invitee signs up and sees
  "You're invited to see progress for Mealea" -> Accept is refused until the email is confirmed (correct: "Confirm
  your email address before accepting an invitation"). But a grandparent who only helps landed on "Add your first
  child to start their learning journey" plus a large "No children yet / Add your first child" card. Now, when the
  adult helps with (or is invited to help with) another family's child and has none of their own, the lead says "The
  children you help with, and your own if you add them" and the card becomes one quiet line, "No children of your own
  here yet. Add a child of your own". `CaringSection` reports its count to the home (no second request). A new
  parent with no caregiving still gets the full welcome. **Browser verified** (both cases); **Tested**: Vitest 400,
  tsc, eslint. Khmer `home.leadHelping/ownNone/addOwn` drafted (PD-14).
- 2026-10-28: **Settings coverage.** Every `${...}` setting `application.yaml` reads is named in the deployment or
  payments docs except two - `PAYMENTS_MANUAL_LIFETIME` (P14D) and `PAYMENTS_SWEEP_INTERVAL` (PT5M), now in
  `docs/PAYMENTS.md`; the frontend reads no build-time settings. Bundle check: entry 113 KB gzip, admin/Studio/account
  split into their own chunks, hero art in WebP with small variants - nothing to change.
- 2026-10-28: **Child-device linking walked (no change needed).** New family, child Vanna: "Make a code" -> a second
  browser (tablet size, not signed in) at `/child-device` types it -> "Waiting for your grown-up to say yes" -> the
  parent is asked "Is this Vanna's device?" -> Yes -> the device opens `/learn/<Vanna>` ("Hi Vanna! A grown-up needs
  to choose what you'll learn first"); sent to `/parent`, it lands back in Vanna's space. BRD section 5 gains the
  device and caregiver workflows.
- 2026-10-28: **Khmer at 320 px found subject cards clipped; every breakpoint-only grid now has a base column.**
  Khmer pass at 320 px on today's changed screens (sign-in, both register modes, add a child aged 3): no page overflow
  and the new Khmer present - but in "Add a child" the subject cards were 250 px wide in a 208 px column, cut off by
  the card's rounded frame (not a page overflow, so the earlier checks missed it). Cause as before: a grid with
  columns only from a breakpoint grows its implicit column to its widest content. All 42 `className="grid ..."` that
  set columns only from `sm/md/lg/xl` up now have `grid-cols-1` below it (a scripted, uniform change; content that
  fits is unaffected). **Browser verified**: the cards are 208 px in a 240 px panel; regression - landing height
  unchanged (14,932 px at 360), axe clean on /, /try, /try/parent; family home and child page, admin overview /
  payments / schools / learning and teacher home at 1280 and 360 px: 0 overflow. **Tested**: Vitest 400, tsc.
  (Grids built with `cn(...)`: none set columns only from a breakpoint.)
- 2026-10-28: **CHECKPOINT - end of this usage window (owner: no deployment this window).** Pushed through the commit
  after `1e6262e`. **The coworker preview still serves `7705de8`** (verified earlier today at
  `immediate-marketplace-complexity-optimal.trycloudflare.com`); everything after it is **not live**: long names on
  the school side and family settings, "Try again" hidden for 403/404, principal and sign-in copy, the teacher's
  joined notice, the caregiver home, the grid base columns, docs. **Next session, in order**: (1) confirm CI is green
  on the latest commit; (2) continue product work; (3) at the reserve, read `D:/bk-work/preview/tunnel.url` (it
  changes on restart), run `bash /d/bk-work/preview/preview.sh update`, then `browser-check.mjs`, `journey-check.mjs`
  and a check of today's changes through the link. Still open for the owner: authorisation to fix the two
  order-dependent tests; CLAUDE.md section 25's stale note; D-18, D-19, M11, M12; legal drafts' `[OWNER]` items.
- 2026-10-28: **Family data export checked (no change needed).** As the seed parent, Account -> "Download my family's
  data" (headless Chromium): `bookeyond-kid-data-2026-10-28.json` with `format`, `exportedAt`, `account`, `plan`,
  `learningDayZone`, `children` - exactly this family's three children, their answers with hint use (23); no password
  hash, no tokens, no other family's children. Also: the seed child Rithy's "Up next" is a curriculum step and his
  path has only curriculum items - the teacher-assigned items in the dev database belong to E2E children of the same
  name (not a defect).
- 2026-10-28: **"Sign out everywhere" checked (no change needed).** One new parent signed in on two browsers; Account ->
  "Sign out everywhere" -> "Yes" on the first sends it to sign-in, and the second, on its next page load, is sent to
  sign-in too (immediate revocation, PD-02).
- 2026-10-28: **Deleting a child walked; a refused password is now read out.** New family, child Chenda: "Delete
  Chenda..." -> wrong password -> correct one -> the family home shows "Will be deleted on November 4, 2026. Until then
  you can keep Chenda." with "Keep Chenda" (7-day grace). The wrong-password message was shown and tied to the field
  (`aria-describedby`, `aria-invalid`), but focus stayed on the button, so a screen reader wouldn't read it. Both
  password-confirmed forms in `Deletion.tsx` (delete a child, delete the account) now move focus to the password field
  when the password is refused. **Browser verified**: after the wrong password, focus is on the password input, invalid,
  described by "That isn't your current password." **Tested**: Vitest 400, tsc, eslint. Not changed: other forms that
  show errors only on their field (a wider pass for later).
- 2026-10-28: **CHECKPOINT - preview update waiting for the owner's approval.** The usage window had not reset (90%
  used at the start of this turn), so no new product work; straight to the reserved phase. CI green on all four jobs
  for `869ec82` (the latest code). The link `immediate-marketplace-complexity-optimal.trycloudflare.com` is up, the
  gate holds (anonymous 401), and it serves `index-DeqW-Tb0.js` = the `7705de8` build. `preview.sh update` was refused
  by the auto-mode safety check ("Production Deploy") and was **not** retried or worked around. **To finish**: the
  owner runs (or approves) `bash /d/bk-work/preview/preview.sh update`, then
  `node /d/bk-work/preview/browser-check.mjs "$(cat /d/bk-work/preview/tunnel.url)"` and `journey-check.mjs` the same
  way (with `PLAYWRIGHT_BROWSERS_PATH=D:/bk-work/pw-browsers`). The environment is unchanged and working.
- 2026-10-28: **CHECKPOINT - coworker preview verified on `3e193a3` (= `869ec82`'s code; the only later change is
  this roadmap), on the original link `immediate-marketplace-complexity-optimal.trycloudflare.com`.** The owner ran
  `preview.sh update`; nothing was redeployed by me. **Verified through the link**: anonymous 401;
  `browser-check.mjs` - all five roles sign in (parent family home, Sophea's progress, child space, Khmer and back;
  teacher class + Studio; unpaid principal's payment panel; paid principal's classes; administrator payments);
  `journey-check.mjs` - served bundle = the new local build (`index-BKDJdX6D.js`), "Let's go" fully on screen at
  360x640, "6 + 4" answered with 10 -> "You got it!", then (re-run, resuming) "9 + 1" with 10 -> "That's right!" and
  **the activity finished** (completion screen), 0 server errors; this window's changes - the role-neutral sign-in
  line, the principal's sign-up line, landing 0 px overflow at 320 px, no 5xx; preview DB at V76. CI green on all four
  jobs for `869ec82`.
- 2026-10-28: **Errors after a submit are read out on every form that uses `FormField`.** The deletion-form fix is now
  general: when an error arrives on a field, focus moves to the form's first invalid field - unless the person is
  already in another field (typing is never interrupted). The two local focus workarounds in `Deletion.tsx` are
  removed. **Browser verified** (dev stack): a wrong password in "Delete <child>" leaves focus on the password field,
  `aria-invalid`, described by the error; deletion still works. **Tested**: Vitest 400, tsc, eslint. Not exercised in a
  browser: the other forms (sign-in, register, reset, account), which share the component.
- 2026-10-28: **M12 discovery, and P1 - a school's academic years and terms.** The owner asked to research and build
  school management. `docs/SCHOOL-MANAGEMENT.md`: what exists (school, classes, codes, teachers, family-granted
  children, class learning, platform billing) and what's missing; research (RAITHub, OpenEduCat, AccessPH - marketing
  claims marked as such; Cambodian calendars differ, so dates are data); a module map; the student and fee workflows;
  the two money domains kept apart (school fees vs Bookeyond subscription); **decisions S1-S6 for the owner** (school
  student record vs family child - recommended: separate records linked only by the family's grant; attendance and
  report-card scope; fee policies; registrar/cashier as staff permissions; online fee payment; legal review); a phased
  plan; ranked further ideas. **P1 built** (no decision needed): V77 `academic_years` (school, name, dates, current,
  archived; one current per school by a partial unique index) and `academic_terms`; `school_classes.academic_year_id`
  (nullable). `AcademicYearService`: dates in order, at most two years, a school's years and a year's terms never
  overlap, terms inside their year, a year can't shrink past its terms, the first year becomes current, making a year
  current clears the old one, the current year can't be archived, classes go only into an active year of their own
  school - all under a lock on the school's years, all audited (null values left out of audit metadata). Calendar
  conflicts answer 409 with words for people; malformed input 400. Endpoints under
  `/api/v1/schools/{id}/academic-years` and `.../classes/{id}/academic-year`, behind `requireSchoolManage` (the
  school's principal or an administrator, school paid). Principal: new "School year" page (add/change years, add/change/
  remove terms, make current, archive/restore) and an academic-year selector on each class in Classes; Khmer for every
  message (ServerText, audit codes, catalog).
  - **Tested**: new `AcademicYearIntegrationTests` (2 scenarios: the whole calendar flow with every rule and its message,
    audit; other principal/teacher/parent refused, another school's class 404, unpaid school refused, admin allowed),
    `ServerTextCoverageTests`, `SchoolStaffIntegrationTests` (11) - green; Vitest 401 of 402 with
    `media.test.tsx` (Studio, untouched) timing out at 5 s under full-suite load - it passes alone (4/4) and passed in CI on
    `be2ece0`; tsc, eslint. **Browser verified (dev stack, headless Chromium, 360 px)**: add a year with two terms, an
    overlapping term refused with "These dates overlap another term of this year.", a second year made current, a class
    placed in it (kept after reload), 0 overflow, axe clean; the page in Khmer (account language) with no English left.
  - **Not done**: P2-P7 wait for S1-S6. Teachers don't see the year yet (nothing of theirs uses it).
- 2026-10-28: **M12 P1 follow-up - year-end rollover.** On an active year, "Copy the classes of <earlier year>" recreates
  that year's active classes by name in this year (names already there are skipped, so a second run adds nothing).
  Only the structure moves: **no children** (a family's consent - S1), no teachers, codes or curricula (the principal's
  choices for the new year); each class is made through `SchoolService.createClass`, so plan limits and the archived-
  school rule hold, all in one transaction; audited (`ACADEMIC_YEAR_CLASSES_COPIED`). `POST
  .../academic-years/{id}/copy-classes`. **Tested**: `AcademicYearIntegrationTests` third scenario (copies the two
  active classes, not the archived one; the new classes have no enrolments or teacher assignments; a repeat adds none;
  same year 400; an archived target 409; audited twice) - green with ServerText coverage. **Browser verified (360 px)**:
  "1 class added to this year." then "This year already has all of those classes."; 0 overflow. Vitest: the known
  `media.test.tsx` load timeout aside, green.
- 2026-10-28: **CI test order isolated; M12 B0 multi-branch foundation.**
  - **CI**: `cecd70f` and `0337462` both failed on the same two order-dependent tests (DueReview:57, Subscription:129) -
    twice in a row, not random: a new test class shifted Surefire's file-system class order. Isolated without touching
    test files: `runOrder=alphabetical` in `pom.xml` (the content tests that create skills and curricula always run
    first). **CI on `c3cc834`: backend job green.** The hidden dependency remains (recorded in the pom); removing it
    needs the owner's authorisation to change those two tests.
  - **B0** (owner requirement: multi-branch is foundational): V78 `school_organizations`; `schools.organization_id`
    NOT NULL, backfilled one organization per existing school (dev DB: 123 schools -> 123 organizations, none
    without); a trigger gives any school inserted without one its own (a frozen test inserts schools by raw SQL - still
    green). A new school creates its organization; the owner adds branches (`POST /api/v1/organizations/{id}/branches`,
    a retry makes no second; each branch waits for its own payment, B1); `GET /api/v1/organizations/mine` (owner: all
    branches; a branch principal: theirs); `POST /api/v1/schools/{id}/academic-years/copy-from/{branchId}` copies a
    sibling branch's years and terms (same rules as by hand; names already there skipped; never across organizations;
    the caller manages both). Principal: "Branches" on the overview (list, add), branch in the switcher, "Use another
    branch's calendar" on the School year page. Design, ownership table (organization vs branch), transfers, receipts
    and decisions B1-B4: `docs/SCHOOL-MANAGEMENT.md` section 8.
  - **Tested**: new `SchoolOrganizationIntegrationTests` (2: single-branch backward compatibility incl. a raw insert;
    branches, retry, per-branch payment, calendar copy once, no crossing organizations, only the owner, audit),
    `AcademicYearIntegrationTests` (3), `ServerTextCoverageTests`, `FamilyDataExportIntegrationTests`,
    `SchoolStaffIntegrationTests` (11), `SelfServiceSchoolOnboardingIntegrationTests` - green. **Browser verified (360 px)**:
    Branches shows "only branch", a branch added ("opens once its own plan is paid"), listed and in the switcher, the
    calendar copy offered, 0 overflow, axe clean, no 5xx.
- 2026-10-28: **CI verified on `25fe220` (all four jobs green); M12 B1 - the organization owner appoints branch
  principals.** Two consecutive green runs (`c3cc834`, `25fe220`) with `runOrder=alphabetical`, after two consecutive
  failures without it: the mitigation holds. **Technical debt (recorded, not fixed)**: `DueReviewIntegrationTests:57`
  and `SubscriptionIntegrationTests:129` still depend on content other test classes create; a reordering or a new
  content-free database would fail them again; the fix (each creates its own skill / second curriculum) needs the
  owner's authorisation to change those tests.
  - **B1**: `PUT /api/v1/organizations/{id}/branches/{schoolId}/principal {email}` - the owner (or a platform admin)
    appoints an existing, active principal account; audited with the previous principal. `SchoolAccessGuard`: the
    organization's owner now passes `requireSchoolManage` for every branch (configuration), while `requireClassStaff`
    (children's data) is unchanged - the owner doesn't see a branch's children unless they run it. Principal UI: each
    branch shows "You run this branch / Run by its own principal" and "Appoint a principal". Role table and the next
    slices (organization dashboard, shared settings with overrides, student records and transfers after S1, a school
    joining an organization - decision B5) in `docs/SCHOOL-MANAGEMENT.md` 8.1-8.2.
  - **Tested**: `SchoolOrganizationIntegrationTests` third scenario (teacher and unknown emails refused with words; the
    appointed principal runs South, not North, can't add branches, sees only South; the owner still manages South's
    calendar and classes but not its class's children; non-owner can't appoint; audited) plus the existing school
    suites (`SchoolAccess` 11, `SchoolFamilyBoundary` 3, `SchoolPaymentAccess` 3, `SchoolStaff` 11), `AcademicYear` 3,
    ServerText - 37 green; frontend principal/school Vitest 28, tsc, eslint. **Not browser verified**: the appoint form
    (API and server rules are covered by the integration test).
- 2026-10-29: **CI green on `203c2fb` (M12 B1). Flexible plan management, slice 1** (owner brief "Flexible
  Subscription and Plan Management"). Discovery found most of the requirement already built (admin-configured
  plans, DRAFT/ACTIVE/RETIRED, frozen active plans, audience-scoped limits enforced on the server, price and limits
  copied into every payment order, paid school plans, invoices, refunds, audit). Built now, without any pricing
  decision:
  - **Duplicate as a new version** (`POST /api/v1/admin/plans/{id}/duplicate`, V79 `based_on_plan_id`): a new draft
    with the original's words, price, self-service choice and limits, linked to it; the original and its subscribers
    are untouched. This is the safe way to change a price or limits. Audited `PLAN_DUPLICATED` with the source code.
  - **Subscriptions now** (`GET /api/v1/admin/subscriptions/summary`, Plans page): per plan, how many are in force,
    end within 30 days, trial, payment overdue, cancelling, ended - read with the same rules that grant access;
    families on the default plan (a count: families are never listed); schools in use without a paid plan; schools
    whose paid plan ends within 30 days, linked to their page for the renewal invoice.
  - Plans list fix: with a plan open beside the list at 1280 px the name column collapsed to almost nothing (names
    broke a letter per line); the list is now compact (name, status, count) while a plan is open side by side.
  - **Designed, not built** (`docs/SUBSCRIPTIONS.md`): organization-scope plans with coverage and pooled places,
    add-ons, feature/module entitlements (never a child-learning feature), availability window, renewal of retired
    versions, trials, introductory versions, promotion codes, grace period; the two money domains (Bookeyond
    subscriptions vs school fees) kept apart. Owner decisions SB1-SB9 listed with recommendations.
  - **Tested**: `PlanManagementIntegrationTests` (2: version keeps the subscriber's plan, price and limits; retired
    plans can be duplicated; school copies keep school levers; refusals 409/400/403; lineage in the schema; audit;
    summary counts by standing, families never listed, a paid school listed ten days before its end, admin only),
    `SubscriptionIntegrationTests` (9), `SchoolPlanAndStorageIntegrationTests` (5), `ServerTextCoverageTests` - green.
    Frontend: `planVersions.test.tsx` (2), admin Vitest 76 (one studio test timed out under the full run's load and
    passes alone - unrelated), tsc, eslint. **Browser verified** (headless, 1280 and 360 px): duplicating a plan,
    the summary for families and schools, no horizontal overflow, axe clean.
- 2026-10-29: **CI green on `3092341`. Studio choice width; subscriptions slice 2 - offer dates.**
  - Child journey walked on a tablet (768 px, wrong answer first): feedback, reveal, completion and next step work;
    the weakest moment is a mistake on content without an authored explanation - a content gap (the seed has
    none), not invented in code. Retrying before the reveal would change what counts as evidence: not done
    without a decision.
  - Teacher authoring walked at 1280 px: writing, checks (missing skill, hint naming the answer), child-view
    preview and publish gating work. **Fixed** (`8aa12ac`): a multiple-choice answer's text box was squeezed to
    about 50 px by six media buttons; it now keeps a usable width (324 px) with the buttons wrapping below.
  - **Offer dates** (V80, `docs/SUBSCRIPTIONS.md` §6): an active plan offered from / until; applied in every place
    someone new takes a plan up; whoever is on it keeps it; the default plan always offered (database CHECK too);
    step-up, audited. Plans list says "Not offered yet" / "No longer offered"; the plan shows "When it is offered".
  - **Tested**: `PlanManagementIntegrationTests` third scenario (not yet / until / ended across family choices,
    requests, checkout, grants; a school plan in a principal's choices, checkout, an administrator's invoice; the
    default plan; retired; 400 on reversed dates; 403 for a parent; audit count; database CHECK) and the payment,
    subscription and school-plan suites - green; `planVersions.test.tsx` 3; admin operations Vitest 22; tsc; eslint.
    **Browser**: the dates form at 1280 and 360 px (axe clean, no overflow) up to the step-up prompt, which asks the
    seed administrator for an authenticator code I don't have - saving through it is covered by the tests above,
    **not browser verified**.
- 2026-10-29: **M12 B2 - organization overview for its owner** (`docs/SCHOOL-MANAGEMENT.md` §8.2 item 1; no
  decision needed). `GET /api/v1/organizations/{id}/overview`: per branch - classes in use, teachers (on staff or
  invited), children with access, current academic year, paid / paid until / payment waiting / not paid; totals over
  branches in use that count a child or a teacher in two branches once (new distinct-count queries). Counts only:
  nobody is named, so oversight never goes round `requireClassStaff`. Owner or platform admin; a branch principal,
  teacher, parent or another principal gets 404. Shown in the owner's Branches section (totals line, figures per
  branch; the unpaid state in the warm warning colour).
  - **Tested**: `OrganizationOverviewIntegrationTests` (three branches, a shared child and teacher counted once, a
    current year, paid and unpaid, nobody named in the response, 404 for four other roles, 401, admin view,
    archived branch listed but not counted; access rows arranged directly - the class-code flow has its own tests),
    `SchoolOrganizationIntegrationTests` 3 - green. Frontend `branchesOverview.test.tsx` 2, principal Vitest 19, tsc,
    eslint. **Browser verified** in English at 1280 and 360 px (axe clean, no overflow, no API errors). Khmer is not
    browser verified: the seed account's language setting keeps the page in English; the Khmer strings are listed
    for review.
- 2026-10-29: **CI on `224fa74` failed** (`PlanOffersTests`, protected: plans built as mocks returned false for the
  new `offeredAt`); fixed in `9982eef` without touching the test (`SubscriptionPlan.withinOfferDates`, read through
  getters); unit suite 262/262 locally. Lesson recorded: run the unit suite, not only integration classes, before a
  push. **Plan history**: each plan's own audit trail (created, revised, activated, duplicated, dated, made default,
  retired) now shows under the plan on the Plans page, reloaded after a change made there - browser verified at 1280
  and 360 px, admin Vitest 22. **Role sweep** (phone 360 and tablet 768, parent / teacher / principal / admin, every
  navigation page plus detail pages and the Studio editors): no overflow, no serious or critical axe violation, no
  API or script error.
- 2026-10-29: **CI green on `9982eef`** (offer dates, organization overview, the PlanOffers fix). **Studio: "Make a
  copy" of a question** - the way to write a variant ("5 + 4" to "6 + 3") without retyping.
  `POST /api/v1/studio/questions/{id}/duplicate` (who may edit the question may copy it): a new question whose first
  draft is the original's latest version - prompt, answer, choices, media, hint, explanation, difficulty, skills and
  translations (`QuestionService.copyAsDraft`, reused from lesson sharing). The copy belongs to the original's owner,
  also when an administrator or a principal copies a teacher's question, so its media stay that owner's own files;
  audited `QUESTION_DUPLICATED`. The editor shows "Make a copy" beside "Manage..." and opens the copy with a note
  saying what it is.
  - **Tested**: `QuestionDuplicateIntegrationTests` (everything copied incl. the Khmer prompt, owner, the copy's
    change leaves the original, a published original copied as a draft, an administrator's copy stays the teacher's,
    another teacher / a parent / an unknown id refused, audit count), `ServerTextCoverageTests`; backend unit suite 0
    failures; studio Vitest 54; tsc; eslint. **Browser verified** as a teacher at 1280 and 360 px: copy made, the copy
    opens with the note, no API error, axe clean, no overflow.
- 2026-10-29: **Studio: "Make a copy" of an activity** - `POST /api/v1/studio/activities/{id}/duplicate {title}`:
  a new activity in the same lesson from the latest version - instructions, difficulty, time, skills, introduction
  (the lesson owner's own media) and the same questions in the same order with the same points. Questions are
  reused, not copied; one whose version in the original was since replaced by a newer published version is taken at
  that version (an activity draft only takes a question's draft or current published version), counted in the audit
  (`ACTIVITY_DUPLICATED`, `atNewerVersion`). The editor asks for the copy's title (prefilled) and opens the copy with
  a note. **Tested**: `QuestionDuplicateIntegrationTests` second scenario (settings, introduction, skills, items and
  points, the newer version, questions not duplicated, original unchanged, blank title 400, another teacher and a
  parent refused, audit) - green; clean backend unit suite 200/200 (the earlier 262/263 counts included stale
  integration reports); studio Vitest 54. **Browser verified** as an administrator at 1280 and 360 px (title asked,
  copy opened with the note and a working child preview; no API error; axe clean; no overflow).
- 2026-10-29: **A school's payments on its admin page** (subscription history per customer): `GET
  /api/v1/admin/payments?schoolId=` (with or without `status`), "This school's payments" (latest five, each opening on
  the Payments page filtered to the school, with "See all") and a "Only one school's payments are shown / Show every
  payment" note there. **Tested**: `OrganizationOverviewIntegrationTests` second scenario (two schools, one's
  payments only, with status, an unknown school, a principal refused), `PaymentIntegrationTests` 4 - green; unit suite
  200/200; admin Vitest 22. **Browser verified** at 1280 and 360 px (axe clean, no overflow, no API error).
  **Khmer browser check** of today's screens at 360 px (the seed accounts switched to Khmer through the API and put
  back to English afterwards): the owner's branch figures, Plans (subscriptions now, offer dates, history) and the
  Studio's "Make a copy" - Khmer throughout, no English left, no overflow, axe clean. **Child session, accessibility
  tree** (768 px): the question is the page heading, the answer box and number pad are labelled, "Check" is disabled
  until there is an answer, feedback is a status region, focus moves to "Next" - tree-level evidence, not a real
  screen reader.
- 2026-10-29: **A family's payments on its admin plan card** (after an administrator finds the family by a parent's
  exact address): `GET /api/v1/admin/payments?familyId=`, the shared `CustomerPayments` list (schools and families) and
  "Only one family's payments are shown" on the Payments page. **Tested**: `PlanManagementIntegrationTests` fourth
  scenario (a real family checkout with the test simulator; that family's payments only, with status; another family
  none; the parent refused) - green; unit suite 200/200; admin Vitest 22; tsc; eslint. Not browser verified (no
  family in the dev data has a payment; the shared list is the school list already verified).
- 2026-10-29: **Learning from a mistake - the author's hint after a wrong answer.** Traced end to end: an explanation
  is written in the Studio (with a `NO_EXPLANATION` warning when missing), stored per version, returned only with a
  recorded answer and shown under "Why"; learner content is served in its authored language only (no translation is
  read on the learner side - Battle 3, no invented fallback). The developer seed has no explanations or hints
  (D-19: real curriculum authoring is the owner's), so nothing was written into content. **Changed** (a deliberate
  refinement of Y41, where the hint was gone after answering): after a **wrong** answer to a question **without an
  explanation**, the verdict shows the author's hint under "A way to think about it" - a strategy in the educator's
  own words, which never gives the answer away, so a mistake is met with more than the right answer. An explanation,
  when written, still takes precedence; a right answer adds no hint. The Studio's hint help and the child-view preview
  say so ("After a wrong answer, the child also sees your hint"). Retrying before the answer is revealed was **not**
  added: it changes what counts as evidence (first attempt vs. practice, as D-16 did for hints) - a decision for the
  owner (recorded as an open learning-design question).
  - **Tested**: `mistakeHelp.test.tsx` (hint after a wrong answer without explanation, announced in the status; only
    the explanation when there is one; nothing on a right answer or a blank hint); child, marketing and studio Vitest
    99 incl. the protected `guidance.test.tsx`. **Browser verified** in the Studio preview at 1280 px (a copy without
    its explanation shows the hint after a try). Not browser verified in a child's real session: no seed question has
    a hint; the session renders the same `AnswerVerdict`.
- 2026-10-29: **CI green on `2720e8a` and `81d4934`.** Authoring forms re-measured for every question type at 1280,
  768 and 360 px: no squeezed text box left (the sorting group picker sizes to its longest group name; matching on a
  phone keeps 126 px boxes, usable). A teacher's whole journey walked at tablet width - new activity in a new lesson,
  a question with hint and explanation, checks, publish, the published page and "Get it to children", then a class's
  skill page and "Assign practice": complete and clear. **Writing guide** no longer says "aged 4 to 9" (D-09, 3-12).
  **D-20** recorded (a second try before the answer is shown). **Operations see school groups**: the admin school
  record carries its organization and branch count; a branch's admin page shows "Part of ..." with the group's totals
  and every branch's classes, children and payment, linking to each (counts only, from the organization overview).
  **Tested**: `OrganizationOverviewIntegrationTests` (organization and branch count on the admin school record, an
  independent school counts 1), `AdminInsightIntegrationTests` 4, `AdminOperationsIntegrationTests` 4 - green; unit
  suite 200/200; admin Vitest 77. **Browser verified** at 1280 and 360 px (axe clean, no overflow, no API error).
- 2026-10-29: **Child home: one way in when a review is due on the path the child carries on with.** The separate
  "A review is waiting" card pointed at the same activity as the big "Keep playing" card (which already says "Time to
  review ... so it sticks!"), so a child saw two buttons doing the same thing (and a screen reader heard two links).
  The card now shows only for a review on another subject. **Tested**: `homeReview.test.tsx` (one path: no second
  card - this failed before the fix; two paths: the card points at the other subject), child Vitest 45 incl. the
  protected `home.test.tsx`. **Browser verified** with the seed child at 360, 768 and 1280 px (one play link, the
  review named on the card).
- 2026-10-29: **Studio: unsaved question edits are no longer lost silently.** The question form now knows when what
  it would save differs from what it was given (spaces around words don't count; a save resets it). While it does,
  "Make a copy" refuses with "Save your changes first: a copy is made from what is saved", and reloading or closing
  the tab asks first (`beforeunload`; the builder's question forms and the New question form too). Links inside the
  app are not blocked: the app uses `BrowserRouter`, which has no navigation blocker - moving to a data router is a
  larger change, recorded rather than done. A teacher's assign-practice flow was also walked at tablet width (button
  disabled until children are chosen, "Assigned to 2 children.", axe clean). **Tested**: `unsavedDraft.test.tsx` 2,
  studio and child Vitest 101. **Browser verified** at 1280 px (refusal shown, no navigation; reload asks; after
  saving, the copy is made).
- 2026-10-29: **Studio authoring: reuse shows what is reused; "Teach it first" can't be lost at publish.** The builder's
  "Use a question you already published" listed each question's *latest* wording but added its *published* version, so
  a newer draft's text could be shown for a question children would get in its older form; the question bank now
  carries `publishedPrompt` and the picker shows it, with the question's type and skills, over two lines instead of
  one truncated line. The activity's introduction saves on its own: typed but unsaved, it now says "Not saved yet.",
  its button turns solid, reloading asks first, and publishing is refused with "Save \"Teach it first\" before
  publishing..." until it is saved. **Tested**: `QuestionDuplicateIntegrationTests` (published vs latest wording with
  a newer draft), `ContentStudioIntegrationTests` 9; `introUnsaved.test.tsx`; admin Vitest 80. **Browser verified** at
  768 px (refused with the unsaved introduction, then saved and published). Not done: the activity's "More settings"
  form has the same save-separately shape (recorded).
- 2026-10-29: **Content in two languages, slice 1 - questions in English and Khmer** (owner requirement; design,
  rule L-1 and the inventory of what still has one language in `LOCALIZATION.md` "Content in two languages"). A
  question version's English stays in its columns; its Khmer goes to the existing, until now unused
  `content_localizations` (no migration): prompt, hint, explanation, picture description and every choice / pair side /
  item / group label (`QuestionTranslations`). Saved together with the English in one request (`khmer`), carried to new
  drafts, "Make a copy" and shared-lesson copies with option ids mapped. A child reading Khmer gets a question in
  Khmer only when its Khmer is complete, otherwise the whole question in English; the explanation and the revealed
  right answer follow; the item says `language: "km"` and the child's screen sets `lang="km"`. Short answers are not
  translated (scored on the author's words). Studio: "Writing in: English | ខ្មែរ" with the count written, Khmer
  fields labelled and marked `lang`, the English shown beside/under them; preview "As a child reading: English |
  ខ្មែរ"; publish warning `KHMER_INCOMPLETE`. Nothing is translated automatically.
  - **Tested**: `BilingualQuestionIntegrationTests` (create and revise with both; preview per language; complete vs
    incomplete; the warning; a new draft and a copy keep the Khmer on the new options; short answer keeps none; a
    child's activity served in Khmer and in English by `Accept-Language`; a wrong answer's explanation and right answer
    in Khmer; five rows stored), the content/learning/demo/activity/lesson/question/media suites (78, 0 failed);
    `bilingualQuestion.test.tsx` (English, switch, Khmer, back, both saved; saved Khmer reopened per row; short answer
    disabled), studio/child/i18n Vitest 245 incl. the protected payload tests (Khmer is sent only when there is some).
    **Browser verified** at 1280 and 360 px: written in English, switched, written in Khmer, saved, reloaded - both
    back in their fields; the preview as a Khmer reader in Khmer with `lang="km"`; axe clean; no overflow; no API
    error. Not browser verified: a child's real Khmer session (covered by the integration test).
- 2026-10-29: **Content in two languages, slice 2 - activities.** An activity version's name, instructions,
  "Teach it first" text and its picture's description in Khmer (`ActivityTranslations`, `ACTIVITY_VERSION` rows - no
  migration), written by the two saves the Studio already has (More settings: name and instructions; Teach it first:
  text and description), carried to the next version (a copy keeps all but the name, which is new). A child reading
  Khmer gets the activity's own words in Khmer when all of them are translated; the name on the child's path, home
  and shelf follows the same rule (`LearningJourneyService`). Studio: the shared `ContentLanguageSwitch` /
  `Translated` (`BilingualFields.tsx`, used by questions too) on both forms, with honest status lines (the
  introduction's says children see it once the name and instructions are in Khmer too). **Tested**:
  `BilingualQuestionIntegrationTests` (activity Khmer saved by both saves without either clearing the other, served
  in Khmer and English, the path's step name in both, a new version keeps it) and the learning / parent / studio /
  lesson-sharing suites - 44, 0 failed; backend unit 200/200; `activityKhmer.test.tsx`; frontend 420 (one protected
  `media.test.tsx` case timed out under the full run's load and passes alone - known). **Browser verified** at 768 px:
  introduction, name and instructions written in both languages, saved, reloaded - all back; axe clean; no overflow;
  no API error.
- 2026-10-29: **Content in two languages, slice 3 - subject, skill, chapter and curriculum names.** V81 widens
  `content_localizations`' content types to `SUBJECT`, `SKILL`, `LEARNING_OBJECTIVE` and `CURRICULUM` (no row
  changes); `CatalogNames` keeps each one's Khmer name and description and gives a Khmer reader the Khmer per name,
  whenever it is written (a name is a self-contained label, unlike a question - L-1 stays as it is). Read in Khmer on
  the family catalog, the child's path (curriculum, subject, chapters, step skills), the skills page, "what changed"
  and the skill or chapter inside a parent insight's sentence (`InsightNames`, swapping the recorded name for its
  Khmer; renamed since: left as recorded). Studio: `CatalogNameFields` (the shared language switch) in every Manage
  dialog for these and in the new subject / skill / chapter / curriculum forms; a save that never touched Khmer sends
  none. Teachers' class and assignable-curricula lists name curricula and subjects the same way. Reads: a Khmer
  reader's names are read per curriculum shown, not per name (`CatalogNames.prefetch` and a per-transaction cache);
  doing so found and fixed a pre-existing one-query-per-skill read on the child's path, for every reader (a journey
  with a six-skill step: 24 -> 19 statements in English, 24 in Khmer). **Tested**:
  `BilingualCatalogNamesIntegrationTests` 2/2 (create with Khmer, edit keeps or replaces it, clearing removes it, the
  family catalog / path / skills page in both languages, a parent can't write names - 403; and the Khmer reads stay
  within a fixed allowance per curriculum); `ReadQueryScalingIntegrationTests`, learning journey, progress changes,
  parent insights, family catalog, Studio, school access, caregiver and account-language suites - 48, 0 failed;
  `InsightNamesTests` 3/3, `InsightTextTests` 4/4; `catalogKhmer.test.tsx` + the admin and i18n suites 224/224.
  **Browser verified** at 360 px: a skill created with a Khmer name, its description's Khmer added in Manage, reloaded
  - both languages back; axe clean in the dialog; no overflow; no API error. Findings (recorded, not fixed): the
  Studio catalog reads each row's links and Khmer one row at a time (pre-existing pattern - **future**, performance);
  a parent insight's name costs two reads per insight for a Khmer reader (a handful per page - **future**).
- 2026-10-30: **Content in two languages - plans.** V82 adds `name_km` / `description_km` to `subscription_plans`
  (NULL = not translated; existing plans unchanged). Written on a draft with the English, carried to a duplicate,
  fixed once offered like the English. Families (own plan, choices, limit offers, open request, request emails) and
  principals (own plan, plans to pay for) read them in their language; administrators manage English as English with
  the Khmer beside it; payment orders keep the English name they were made with. Admin -> Plans: the shared language
  switch in the draft form; an offered plan shows its Khmer read-only, or how to give it some. **Decision recorded
  (L-2)**: may an offered plan's Khmer be added or corrected (today: no - duplicate it); recommendation: Khmer only,
  audited. **Tested**: `PlanKhmerIntegrationTests` (create with Khmer, a save without Khmer keeps it, one with Khmer
  replaces it, reopened, an administrator reading Khmer still edits English, refused once offered (409) and unchanged,
  a duplicate carries both, the default plan with no Khmer reads English, a family reads Khmer in Khmer and English in
  English), plan management / subscriptions / payments / school payment access - 21, 0 failed; frontend admin, parent,
  school and i18n suites 305/305. **Browser verified** at 360 px: a new draft's Khmer name and description written,
  English unchanged after switching, saved, reloaded - both back; axe clean; no overflow; no API error. **Not browser
  verified**: the family's plan page in Khmer (putting a family on a plan needs the seed administrator's authenticator
  code) - covered by the integration test. Media descriptions checked and set aside: authoring aids only (inventory 5).
- 2026-10-30: **Finding (high - learning integrity), fixed: a child's first screen was fetched in the parent's
  language.** Found by browser-checking slice 3 with the seed family: the parent reading Khmer, the child English - the
  child's home showed Khmer chapter, skill and subject names under an English screen. Cause: the child's pages fetched
  their content in parallel with the child (whose language sets the space's), so those requests went out in the
  parent's language; nothing fetched again when the child changed language. It affected every bilingual slice
  (questions, activities, names). Fix: `ChildLanguageGate` on `/learn/:childId` - the child's language is in force before
  any page of the space asks for anything (known once per tab), and a language change shows the page afresh (home,
  shelf, buddy; the player has no language button). **Tested**: `childLanguage.test.tsx` (the first request is in the
  child's language under a parent of the other; a change shows the page again in the new one) and the child suites -
  49 passed. **Browser verified** at 360 px with the seed family in all three combinations: parent Khmer / child
  English - all English; parent English / child Khmer - Khmer screen with Khmer chapter, skill and subject names (the
  activity names English: no Khmer written for them, rule L-1); parent Khmer - the parent's Progress tab names chapters
  and skills in Khmer. Also fixed on the way: the garden link's summary on the child's home was below contrast
  (3.59:1, `text-rice-deep/80`) - now full `text-rice-deep`; axe clean.
- 2026-10-30: **Localization audit of the remaining forms** (inventory in `LOCALIZATION.md`): landing page already
  bilingual (F1a; its plans section is site copy); lesson titles are teachers-only; age groups are seeded reference
  data shown beside a localized age range (future, with D-19); assessments and challenges have no screen. Nothing else
  that a child, family or principal reads is single-language.
- 2026-10-30: **Authoring: an activity's "More settings" keep unsaved changes** (follow-up recorded with slice 2).
  Instructions, difficulty, time and their Khmer now say "Not saved yet." beside a filled Save button, and leaving or
  reloading the page asks first - like a question's form and the introduction; after a save nothing is left to warn
  about. **And a link inside the app now asks too** (the router has no navigation blocker - `BrowserRouter` - so
  `useUnsavedGuard` reads link clicks before the router does; one prompt however many forms are unsaved; new tabs,
  downloads, same-page and modified clicks are left alone). It serves the question form, the introduction and More
  settings, replacing their three copies of the reload prompt. Not covered: the browser's Back button (it can't be
  held without a data router - recorded, future). **Tested**: `detailsUnsaved.test.tsx` 2/2 (English and Khmer
  changes; cleared once saved), `unsavedLinks.test.tsx` 2/2 (asks; Stay keeps the page and the text; Leave goes;
  nothing unsaved - no question) and the Studio suites 66/66. **Browser verified** at 360 px: typed, notice shown,
  the Studio link asked and Stay kept the page, reload asked and kept the text, saved, notice gone. The same guard now
  covers a draft plan's form (Admin -> Plans: words in both languages, limits, price); browser verified at 1280 px -
  an admin link asked with unsaved edits and Stay kept them; after Save the link left without asking.
- 2026-10-30: **Content in two languages - an activity previewed per language.** A question's preview had "As a child
  reading: English | ខ្មែរ"; a whole activity's "Try it as a child" showed only the English, so an author could not see
  what a Khmer-reading child gets before publishing. `GET .../activity-versions/{id}/preview?language=en|km` now
  builds the activity exactly as the child's reader does (one shared factory, `ActivityVersionDetailResponse.of`, for
  both): its own words in Khmer only when complete, each question by its own Khmer; the response says its
  `language`. The Studio's Try it has the same switch and says what a Khmer reader would still get in English (the
  activity's words; "2 of 3 questions"); Khmer text is marked `lang="km"`. **Tested**:
  `ActivityPreviewLanguageIntegrationTests` (incomplete activity Khmer - English words, questions each by their own;
  complete - Khmer; English and as written unchanged; `fr` refused 400; a parent 403), with the bilingual question,
  activity builder and Studio suites - 14, 0 failed; frontend Studio and i18n suites. **Browser verified** at 768 px:
  an activity written in both languages - Try it in English, then in Khmer with the Khmer instructions and
  introduction, marked `lang="km"`; no API error. **Finding (future, decision)**: English shown to a Khmer reader is
  not marked `lang="en"` (it inherits the page's Khmer); the model says base text is English, but text typed in Khmer
  into the English fields before the bilingual inputs existed would then be marked wrong - mark by the model or leave
  unmarked is the owner's call.
- 2026-10-30: **Studio search finds content by its Khmer too.** The question bank matched only a question's English
  prompt and the lessons list only English titles, so an author couldn't find what they had written in Khmer. The
  bank now also matches the latest version's Khmer prompt; lessons, an activity's Khmer name in any of its versions
  (`content_localizations`, unique per target - no new index). **Tested**: `StudioKhmerSearchIntegrationTests` (a Khmer
  word finds only its question; English unchanged; a word in neither finds nothing; a lesson by its activity's Khmer
  name) with the Studio, lesson sharing and read-scaling suites - 13, 0 failed. Not browser-verified (the search box
  is unchanged and sends the words as typed). **Fixed the same day**: a lesson found by an activity's Khmer name
  listed none of its activities (the list under a found lesson was filtered again in memory on the English names
  only); it is now matched the same way, with one more query (`ActivityTranslations.titles`), and the test asserts the
  matched activity is listed. **Browser verified** at 360 px after the fix: "រាប់បក្សី" in the activities search finds
  the lesson and lists its activity "Count the birds"; no API error.
- 2026-10-30: **Phone sweep after these changes.** Parent, teacher, principal and administrator navigation at 360 and
  768 px, the Studio's pages and editors, and the child's home, shelf and buddy in Khmer at 320 px: no overflow, no
  serious or critical axe finding, no API or page error.
- 2026-10-30: **The Studio catalog is read together** (the "future, performance" finding of slice 3, done). A subject's
  catalog read each skill's prerequisites, each chapter's skills and each one's Khmer row by row - three queries per
  row. Now one query each for all of them (`findBySkillIdIn`, `findByLearningObjectiveIdIn`, `CatalogNames.preload`,
  which, unlike `prefetch`, reads the Khmer for any reader: the Studio always shows both languages). **Tested**:
  `StudioCatalogReadsIntegrationTests` (9 statements with 1 skill and with 8 skills, prerequisites, chapter links and
  Khmer names - and the same contents) with the Studio, lifecycle and catalog-names suites - 14, 0 failed.
- 2026-10-30: **The question bank says how far each question's Khmer has got** ("in Khmer too", "Khmer not
  finished", "English only"; nothing for a typed-word answer, which isn't translated), so an author sees what is left
  to translate without opening each question. Two more queries per page, not per question (`QuestionTranslations.of`
  for many versions, options in one query). **Tested**: `BankKhmerStatusIntegrationTests` (complete, partly, none,
  not translatable) with the Studio, bilingual question and copy suites - 13, 0 failed; Studio Vitest 66/66.
  **Browser verified** at 360 px: the seed bank's rows show their status; no overflow, axe clean, no API error.
  (The "reuse a question" picker now shows the *published* version's own status - see the 2026-10-30 entry
  "reuse picker".)
- 2026-10-30: **A skill explained (X19) names activities and curricula in the reader's language.** The page read
  them in SQL as written while its skill and chapter names were localized, so a Khmer-reading parent saw both
  languages on one page. Each activity is now named by the path's rule (its Khmer when complete,
  `ActivityTranslations.nameForReader`) and each curriculum by `CatalogNames` - for a Khmer reader only (an English
  reader costs no read). **Tested**: `SkillStoryKhmerIntegrationTests` (a waiting step in Khmer and in English) with
  the learning journey and read-scaling suites - 10, 0 failed.
- 2026-10-30: **The Studio catalog shows each skill's and chapter's Khmer name** under the English (`lang="km"`), or -
  to a catalog administrator, who can add it - "No Khmer name yet". **Tested**: Studio and i18n Vitest 205/205.
  **Browser verified** at 360 px: one Khmer name and two "none yet" in the seed subject; no overflow; axe clean.
- 2026-10-30: **A teacher's class practice names activities like the path** (the practice to choose from, and each
  child's latest placed practice): in Khmer for a teacher reading Khmer where the activity's Khmer is complete
  (`ActivityTranslations.nameForReader`). **Tested**: school access and school/family boundary suites - 14, 0 failed
  (the English behaviour); the Khmer rule is the one tested with the skill story and the path - the class view in
  Khmer is **not separately tested**.
- 2026-10-30: **L-2 decided by the owner and built: an offered plan's Khmer can be added or corrected.** "Edit the
  Khmer words" on any plan (`PUT /admin/plans/{id}/khmer`, step-up, `SubscriptionPlan#retranslate`) changes only
  `name_km` / `description_km`; English, price, currency, interval, limits, status, offer dates, subscriptions and
  payment orders are untouched, and nothing is activated, retired, renewed or moved. Audited `PLAN_TRANSLATED` with
  the Khmer before and after (Khmer wording added to the audit log's codes). The form shows each English beside its
  Khmer field and asks before unsaved Khmer is lost. **Tested**: `PlanTranslationIntegrationTests` (an active plan with
  a subscribed family: Khmer added then corrected; every other column, the limits and the subscription row unchanged;
  the family reads the new Khmer, English unchanged; two audit events with before/after; a parent 403; an administrator
  without step-up 403 `stepUpRequired` and nothing written) with the plan Khmer and plan management suites - 6, 0
  failed; admin operations and i18n Vitest 161/161. **Not browser verified**: saving needs the seed administrator's
  authenticator code for the step-up.
- 2026-10-30: **Screen-reader language of content shown as written - designed, not built** (owner's direction: mark
  only from explicit metadata, never guess, leave unknown unmarked). Design and migration implications in
  `LOCALIZATION.md` ("Screen-reader language ..."): a nullable `text_language` on question and activity versions,
  set by the bilingual Studio, NULL (unmarked) for everything earlier. Next in the localization order.
- 2026-10-30: **Screen-reader language - built for questions (V83).** `question_versions.text_language` ('en' or
  NULL, CHECK; no backfill - unknown stays unknown) is set when the Studio creates or saves a draft and carried to the
  next draft and to copies; the served item adds `writtenIn` ("en"/null) beside the unchanged `language` ("km"/null),
  and the child's question is marked `lang` from them, unmarked when neither is known. **Tested**:
  `TextLanguageIntegrationTests` (Studio-written: "en"; in Khmer: "km" and no `writtenIn`; next draft keeps "en";
  an older version (NULL) says nothing until a draft of it is saved) with the bilingual question, copy, Studio,
  lesson-sharing and activity-preview suites - 16, 0 failed; `questionLanguage.test.tsx` (km / en / unmarked) and
  the child suites 50/50. Not browser verified (the mark is an attribute; its effect is in screen readers).
- 2026-10-30: **Screen-reader language - built for activities (V84)**: `activity_versions.text_language`, set by the
  Studio's three saves, carried by next drafts, copies and shared lessons; `writtenIn` on the activity; the child's
  player marks the activity's name, instructions and introduction (`language ?? writtenIn`, unmarked when unknown).
  **Tested**: `ActivityTextLanguageIntegrationTests` (Studio-written "en"; complete Khmer "km"; a copy keeps "en";
  unknown says nothing until saved) with the activity preview, bilingual, builder and lesson-sharing suites - 8, 0
  failed; child Vitest 50/50. Not marked yet: the "look again" introduction during the questions.
- 2026-10-30: **Lesson titles in English and Khmer (V85)** - owner's widened brief (teacher-read fields too).
  `LESSON` joins the content types; the lesson's Manage dialog writes the Khmer title beside the English
  (`CatalogNameFields`, "Title"); the Studio's lessons list shows it under the title (`lang="km"`, read together with
  `CatalogNames.preload`); a colleague's copy of a shared lesson carries it. **Tested**:
  `LessonKhmerTitleIntegrationTests` (saved, kept on an English-only rename, removed when emptied, listed) with the
  lesson sharing, lifecycle, catalog-names and search suites - 8, 0 failed; Studio Vitest 66/66. Not browser verified.
  The shared-lesson copy is now asserted too (next entry).
- 2026-10-30: **Two localization details finished.** (1) "Show me again" during the questions marks the introduction
  with its known language (`LookAgain` `lang`, from `language ?? writtenIn`; unknown stays unmarked; the dialog's
  buttons stay in the interface language) - `lookAgainLanguage.test.tsx` (km / en / unmarked, the dialog itself never
  marked), child Vitest 51/51. (2) A shared lesson's Khmer title through the real sharing workflow -
  `SharedLessonKhmerTitleIntegrationTests` (a paid school, author and colleague: the copy keeps "Farm animals" and its
  Khmer; renaming the copy in both languages leaves the original's; the author's later Khmer rename doesn't reach the
  copy) - 1, 0 failed.
- 2026-10-30: **Authoring: the "reuse a question" picker says whether what it adds is in Khmer.** Reusing a question
  adds its *published* version, so the bank's summaries now also carry `publishedKhmer` (that version's status, in
  the same two batch queries), and the activity builder's picker shows it ("in Khmer too", "Khmer not finished",
  "English only"). An author building a bilingual activity can pick questions children reading Khmer will get in
  Khmer. **Tested**: `PublishedKhmerStatusIntegrationTests` (draft only - none; published English with a translated
  newer draft - latest COMPLETE, published NONE; after publishing the draft - COMPLETE) with the bank status and Studio
  suites - 11, 0 failed; Studio Vitest 66/66. Not browser verified.
- 2026-10-30: **Authoring inventory, owner's widened brief (lessons, learning materials, objectives, announcements
  and notices, all meaningful metadata)** - recorded for the next window, in this order: (1) `text_language` above;
  (2) lesson titles - teachers and colleagues read them (shared lessons), so they get a Khmer title through a `LESSON`
  content type (a CHECK change, like V81); (3) school-written communication: today the only free text a school or
  administrator sends families is the plan-request decline message (written once to one family) - announcements and
  notices don't exist yet, so they will be designed bilingual when built (S-decisions); (4) learning-objective
  descriptions and curriculum names are done (V81); lesson bodies (`LessonVersion.body`) have no screen today.
- 2026-11-01: **Product journey review** (owner's request) - `docs/PRODUCT-JOURNEY-REVIEW.md`: journey-by-journey
  coverage matrix, module connections, missing capabilities, open decisions with recommendations (new proposals N-1
  weekly parent summary, R-1 one account with two roles, P-1 landing prices from plan records, C-1 principal review
  of teacher content), and a phased roadmap. Next five tasks, in order: (1) P-1 landing prices from plan records
  (after the owner's yes) or, meanwhile, (1') unsaved guard for Manage dialogs; (2) teacher first-run guide in the
  Studio; (3) school setup checklist for a new principal; (4) park/remove the dormant streak backend (owner's call);
  (5) support read-only view of a child's state for platform admins. Content (D-19) and launch operations remain the
  owner's critical path.
- 2026-11-03: **Two tasks from the journey review's next five, built.** (1') A Manage dialog (a subject's, skill's,
  chapter's, curriculum's or lesson's names, in either language) no longer discards typed names: closing it - the
  close button, Escape or the backdrop - asks first, and links and reload ask too (`useUnsavedGuard`); after a save
  it closes without asking. `manageUnsaved.test.tsx` 2/2 with the lifecycle and catalog tests (8). **Browser
  verified** at 768 px: close and Escape both asked; Stay kept the dialog and the typed name. (2) **A teacher's first
  steps** on their home (`GettingStarted`): join your school, get a class, give it a curriculum, bring children in
  (class code), write an activity of your own - each read from what is true now (memberships, the staff overview,
  the teacher's own lessons; nothing stored), the next one highlighted with where to do it, gone once all are done.
  `gettingStarted.test.tsx` 2/2. **Browser verified** at 360 px: the seed teacher (all five done, checked by API) sees
  no card; with the lesson count stubbed to 0 the card shows 4 of 5, "Write an activity of your own" current with
  "Open the Studio"; no overflow, axe clean. (3) **Not needed as a new task**: a principal's school overview already
  lists what is still to set up. (5) **Support read-only view** reclassified: an administrator reading a child's
  learning is a new privacy boundary - needs the owner's decision (proposed **A-1**: allowed only with the family's
  consent or a support request, audited) before building.
- 2026-11-03: **Learning from mistakes after an activity: "Look again at the tricky ones".** The end of an activity
  said only "You got 1 of 3 right"; a child had no way back to what they missed. Now, when something was answered
  wrong, a closed panel offers each such question in the order asked - what was asked, the answer and, where the
  author wrote one, why - in the child's language with its known language mark (V83). It changes nothing: no second
  try, no new score or evidence (D-20 stays the owner's), and it never stands between the child and "Keep going".
  `trickyOnes` (pure, `tricky.ts`) and `TrickyOnes` - `tricky.test.tsx` 2/2 (only wrong answers, order, answer, why,
  language; closed until opened) and the child suites 194/194. **Browser verified** at 390 px with the seed child:
  two wrong answers -> "Look again at the 2 tricky ones", closed, then "3 + 4 = ? The answer: 7"; no overflow, axe
  clean, no API error. Seen on the way: the seed questions have no explanations, so children get only the answer -
  a content gap (D-19), recorded.
- 2026-11-03: **Educational quality for content production (D-19 support): "How ready the content is".** The
  administrator's learning page already listed skills with no activity and age groups with nothing offered; it now
  also says, for each offered platform curriculum, of the questions its published activities give children: how many
  explain why after a mistake, offer a hint, are read aloud (pre-readers, 3-5) and are complete in Khmer (by the
  child's reader's own rule, of those that can be) - so content work goes where it is short. One query plus three
  batched Khmer reads (`AdminInsightService.readiness`); the first 12 curricula, then all on request. **Tested**:
  `ContentReadinessIntegrationTests` (one fully supported and one plain question -> 2, 1, 1, 0, Khmer 1 of 2; a
  parent 403) with `AdminInsightIntegrationTests` - 5, 0 failed; admin operations and i18n Vitest 163/163. **Browser
  verified** at 360 and 1024 px: 12 of 160 dev curricula with "Show all 160 curricula"; no overflow, axe clean, no API
  error. Seen on the way: the seed curriculum's questions are read aloud 0 of 9 and Khmer 0 of 9 - pre-readers
  (3-5) and Khmer families have no suitable content yet (D-19).
- 2026-11-03: **Parents see what was missed, not only the score.** Each finished activity in a child's recent activity
  with a wrong answer now has "See the questions they missed": read only when opened - the attempt's answers and the
  exact (pinned) version the child played, in the parent's language - each missed question with the answer and the
  author's "why", using the same pairing as the child's "Look again" (`trickyOnes`). No new permission: the family's
  existing access to the child's answers and content. **Tested**: `missedQuestions.test.tsx` 2/2 (nothing read until
  opened; only the missed; a failure says so and retries on reopening), parent and i18n Vitest 213/213. **Browser
  verified** at 360 px on the seed child's Progress tab: "See the 2 questions they missed" -> "3 + 4 = ? The answer:
  7", "5 + 4 = ? The answer: 9"; no overflow, axe clean, no API error.
- 2026-11-03: **Teachers see the questions their class finds hardest** on a skill's follow-up page (teacher and
  principal): at most five, each with how many of the members who answered it got it wrong at least once (children,
  not answers - one child's retries don't weigh more), in the reader's language with the question's known language
  mark - what to reteach, beside who needs practice. Only the class's members (family-granted access), the same
  evidence the page already shows per child; nothing changes. One query plus a version read per listed question
  (`ClassPracticeService.mostMissed`). **Tested**: `SchoolAccessIntegrationTests` 11/11 (the follow-up endpoint
  unchanged for its existing cases); school, teacher, principal and i18n Vitest 173/173. **Not separately tested**: the
  counts themselves (no dedicated integration test - fixture of a paid school, class and granted children); checked
  through the API (one class member's wrong answer -> "2 + 3 = ?", 1 of 1) and **browser verified** at 360 px; no
  overflow, axe clean, no API error.
- 2026-11-03: **The class's hardest questions now have their own test** - `ClassHardestQuestionsIntegrationTests`:
  counts members who answered and who got it wrong, excludes a non-member's wrong answers to the same question, counts
  a child's repeated wrong answers once, and refuses a teacher of another class. **Tested** 1/1.
- 2026-11-03: **Paste questions from a spreadsheet** (Priority 3, content production - the D-19 gap is volume): the
  Studio bank's "Paste from a spreadsheet" page reads rows copied from Sheets/Excel (tab-separated, quoted cells) with
  a header row - type (the Studio's names in English or Khmer), question, answer, choice 1-8, hint, explanation,
  difficulty, and "(km)" columns for Khmer. Multiple choice, true/false, number, short answer and put-in-order; pictures,
  recordings, matching and sorting stay in the question form. The page only maps cells (unknown type, empty question,
  an answer that isn't one of the choices, difficulty out of range); `POST /studio/questions/import` adds every row as a
  draft through the same path as the question form - one transaction, all or none, one subject, at most 50 - and
  returns each draft's publishing checks; audit `QUESTIONS_IMPORTED`. Nothing is published. **Tested**:
  `QuestionImportIntegrationTests` 3/3 (drafts with their checks, hint and complete Khmer; a refused row leaves none;
  two subjects / empty / 51 refused; parent 403; a teacher's imports are theirs only), `sheetImport.test.ts` 7/7, studio
  + teacher Vitest 77/77, backend unit suite 203/203. **Browser verified** (teacher, 360 px and 1280 px): a bad row
  blocks adding with its reason; four rows added as drafts, all "Ready to publish", the first opens in the editor; no
  overflow, axe clean, no API error. Not built: bulk publish of the imported drafts (author publishes each), CSV files.
- 2026-11-03: **Paste questions straight into an activity being written** - the builder's "Paste many from a
  spreadsheet" opens the same page in an "into this activity" mode (`?activity=`); `POST
  /studio/activity-versions/{id}/questions/import` adds each row as a draft question after the activity's own (rows
  without skills take the activity's, as a question written in it does), all or none, only into a draft and only in the
  activity's subject; the author tries the activity and publishes it with its questions (Y42) - no blind bulk publish.
  The import result now lists each draft's issues in words, not only on hover. Also fixes `f3de0f3`'s hard-coded column
  words (i18n check). **Tested**: `ActivityQuestionImportIntegrationTests` 2/2 (appended in order, activity's skill,
  published together, published activity 409, other subject 400 with nothing kept, another teacher refused),
  `QuestionImportIntegrationTests` 3/3, studio + i18n Vitest 218/218. **Browser verified** (teacher, 360 px): two rows
  added, both in the builder; a published activity's paste page says so; no overflow, axe clean, no API error.
- 2026-11-03: **A question template to download** on the paste page: `bookeyond-questions-template.tsv` (UTF-8 with
  a byte-order mark so Excel reads the Khmer) - the header and one sample row per type with English and Khmer columns
  (sample content, not curriculum; Khmer awaits the same native review, PD-14). Pasting the file's text back reads five
  ready rows. **Tested**: `sheetImport.test.ts` (template reads back, its Khmer choices aligned), studio + i18n Vitest
  219/219. **Browser verified**: the download's name, BOM, six lines, and the paste summary "5 questions ready to add".
- 2026-11-03: **Open a CSV or TSV file instead of pasting** on the same page ("Or open a file", up to 1 MB; Excel's
  "CSV UTF-8" keeps Khmer). The parser reads commas when the header row has no tabs (copied cells stay tab-separated,
  so commas inside them stay words). **Tested**: `sheetImport.test.ts` (CSV with a quoted comma; commas inside copied
  cells), studio + i18n Vitest 221/221. **Browser verified** (360 px): a UTF-8 CSV with a BOM, a quoted comma and a
  Khmer column reads as two ready rows; keyboard focus ring on the file control; axe clean. XLSX is not read (save as
  CSV).
- 2026-11-03: **Review, incrementally: the adaptive engine traced from code** - `docs/ADAPTIVE-ENGINE.md` (linked from
  the journey review): inputs and who controls them, the mastery formula (rate 0.2: step 1, 0.556, 0.410, ...), the
  decision table (governing skill = weakest with evidence; reinforce < 0.4 with 2 answers, challenge >= 0.8 with 3),
  eight worked examples, and findings **AE-1** (first prerequisite has no ORDER BY - reinforcement target not
  deterministic with 2+ prerequisites, medium), AE-2 (prerequisite's own mastery ignored), AE-3 (constants), AE-4 (no
  challenge without difficulty), AE-5 (by design). INSPECTED only; no code changed. Next pass: Smart Review, path
  planner, evidence standard, learning days.
- 2026-11-03: **AE-1 fixed** - a skill's prerequisites now come back in the order they were recorded (then by id):
  `SkillPrerequisiteRepository.findBySkillId` gains `ORDER BY created_at, id` (same method, so every caller and mock is
  unchanged), making reinforcement's "first prerequisite" deterministic as `AdaptiveEngine` documents; the Studio's
  prerequisite lists get the same stable order. No schema change. **Tested**: `PrerequisiteOrderIntegrationTests` 1/1
  (stored order differs from recorded order; three reads agree), Studio/cold-start/golden-journey integration 14/14,
  backend unit suite 203/203.
- 2026-11-03: **Review pass 2 and PP-1** - `docs/ADAPTIVE-ENGINE.md` now also traces the evidence standard (what
  adults are told, beside what the engine does), learning days, the starting path and Smart Review (worked schedule
  3 -> 8 -> 22 days; SR-1: Smart Review is remediation while mastery < 0.5, retention of secured skills is D-18; SR-2).
  **PP-1 fixed**: an objective's skills come back in the order they were linked (`ORDER BY created_at, id` on the same
  `findByLearningObjectiveId`), so steps within an objective on a starting path and in the journey builder have a
  stated order. **Tested**: `ObjectiveSkillOrderIntegrationTests` 1/1 (stored order differs from linked order),
  prerequisite/cold-start/golden-journey/Studio/activity-builder integration 18/18, backend unit suite 203/203.
- 2026-11-03: **Review: identity and permissions (R-1)** - `docs/IDENTITY-PERMISSIONS.md`: the permission matrix
  (role gate + the relationship each capability checks), what happens today when one person holds several roles
  (relationships already compose on one account - teacher in two schools, caregiver, org owner running a branch; only
  the role is single, so a parent who teaches needs a second email), options A (today) / B (roles per account, one
  acting role per session - recommended) / C (linked accounts), and the decisions it needs: **R-1a** option, **R-1b**
  principal also TEACHER, **R-1c** ADMIN never combined, **R-1d** what a revoked TEACHER role leaves. Findings ID-1
  (invitation refusal stays generic for privacy), ID-2 (second admin: provisioning only - runbook), ID-3 (~90 role-gate
  sites). INSPECTED only; nothing built - owner decision.
- 2026-11-03: **Publish the pasted questions that are ready** (bank mode of the import): one button publishes, one at a
  time, the imported drafts whose checks pass - each through the same guard and review gate as publishing it alone, so
  one refused (e.g. platform content awaiting review) leaves the others and says why on its row; drafts with issues are
  not offered. Published questions become reusable in the builder ("Use a question you already published"); a child
  still sees a question only inside a published activity. **Tested**: `questionImport.test.tsx` 2/2 (rows sent with the
  chosen skill; only ready ones published, a refusal shown per row and retryable; a server refusal names the sheet row
  and nothing is shown as added), studio + i18n Vitest 223/223. **Browser verified** (teacher, 360 px): "6 + 1 = ?"
  published, the one without an answer left as a draft with its reason; axe clean, no API error.
- 2026-11-03: **The question bank's "Still needs" filter** (content tooling for D-19, beside the admin readiness
  panel): Type / Latest version / **Still needs** / Skill - an explanation, a hint, a read-aloud recording, or Khmer.
  The first three are conditions on the latest version in the bank's query; Khmer uses the same completeness rule the
  bank's Khmer status shows (`QuestionTranslations`, decided in `StudioService` over the subject's candidates, then
  paged), and typed-word questions never need it. Kept in the address (`?needs=`) like the other filters; an unknown
  value is a 400. **Tested**: `BankNeedsIntegrationTests` 1/1 (each need, combined with type, Khmer paging and total,
  refusal), Bank Khmer status + Content Studio integration 10/10, `bankNeeds.test.tsx`, full frontend Vitest 455/455,
  backend unit suite 203/203. **Browser verified** (teacher, 360 and 1280 px): 16 questions -> Khmer 13, explanation 11,
  recording 16; two by two on a phone, one row of four on desktop; axe clean, no API error.
- 2026-11-03: **Product-wide brief (owner, 2026-11-03) checked against what exists** - recorded, not built:
  - **Editable landing page**: already built (F1a `SiteCopyService`: every word of the landing, trust and demo pages,
    nav and footer included, EN/KM overrides with the default restorable; statements V63 with testimonials only when
    attributed, D-10). **Proposed L-3** (needs approval): an FAQ section, and adding/removing/reordering items (FAQ
    entries, feature cards) rather than only rewording fixed ones - the brief lists FAQs; nothing in the roadmap does.
  - **Subjects**: data-driven in the app (catalog in the Studio; each subject's theme follows its code with a stable
    fallback, `subjectTheme.ts`; child and parent lists are grids over whatever subjects exist). **Finding SUB-1**: the
    landing page's subject section is a fixed list of three with hard-coded "ready / in preparation" flags
    (`LandingPage.tsx` SUBJECTS) - it can say something untrue when content changes. **Proposed** (approval, close to
    P-1): drive it from the published curricula, with words still editable.
  - Next build per the brief: transitions and state changes across the product (existing motion tokens first).
- 2026-11-03: **Pages arrive instead of snapping** (owner brief: transitions between screens; constitution: motion for
  orientation and continuity). `components/motion/Arrival`: when the address changes, the area that changed eases in
  (opacity + 6 px rise, 220 ms, the product's "out" curve) with the Web Animations API - nothing remounts, so state,
  fetches and unsaved guards are untouched; still on first load and for reduced motion. Boundaries nest: the Studio
  claims its subject's addresses, so a tab switch moves only the tab's content and the heading and tabs stay put. In
  every workspace (teacher, principal, admin via `WorkspaceShell`), the parent area and the Studio; the child space
  keeps its own `page-in`. **Tested**: `arrival.test.tsx` 2/2 (still on first load; outer on a new page; inner only on a
  tab switch; nothing with reduced motion), full frontend Vitest 457/457. **Browser verified** (teacher, 1280 px):
  Studio tab -> only the tab content animated; Classes, Media library -> the workspace content; reduced motion -> none;
  no page errors.
- 2026-11-03: **Menus drop open from their bar** - the workspaces' phone menu snapped open (`hidden` toggled); it and
  the public site menu now share one `menu-in` (fade + 6 px drop, 180 ms) under `motion-safe`. The site menu used
  `rise-in` without the reduced-motion guard - fixed. Dialogs already open with `pop-in`; closing stays instant (fast
  exits). **Tested**: full frontend Vitest. **Browser verified** (360 px): both menus `menu-in`; reduced motion -> none,
  menus still open.
- 2026-11-03: **A new school's first steps** (journey review next-five item 3, "school setup checklist"): on the
  principal's overview, four steps read from what is true now - a class, a teacher who has **joined** (an invitation
  alone doesn't count), every class with a teacher and a curriculum, a family that has joined - the first not done
  highlighted with where to take it; gone once all are done (the at-a-glance line keeps per-class gaps after that).
  Academic years are left out on purpose (optional). The teacher's first-steps card and this one now share
  `components/workspace/FirstSteps` (same markup; the teacher card's tests pass unchanged). **Tested**:
  `schoolFirstSteps.test.tsx` 3/3, principal + teacher + i18n Vitest, full frontend Vitest. **Browser verified**
  (seed principal, 360 px): "3 of 4 done", current step "Give every class a teacher and a curriculum" (one class lacks
  both); axe clean, no API error.
- 2026-11-03: **Readiness gaps lead to the bank, and count only what the curriculum gives children.** Each gap in the
  admin's content-readiness panel (explanation, hint, read-aloud, Khmer) is now a link to the curriculum's subject bank
  with the matching "Still needs" filter (accessible name keeps the visible count). **Fixed while connecting them**: the
  readiness count matched activities by skill and ignored ownership, so a platform curriculum also counted teachers'
  own activities on the same skill - content its family children never get (PathContentScope, N7); in the dev data 8 of
  one curriculum's 11 counted questions were teacher content. It now counts platform activities only (an activity's
  owner is its lesson's). **Tested**: `ReadinessToBankIntegrationTests` 1/1 (the row names its subject; the bank,
  filtered by the same need, lists the missing question; a teacher's activity on the skill is not counted),
  `ContentReadinessIntegrationTests` 1/1, `readinessLinks.test.tsx`, full frontend Vitest 463/463, backend unit 203/203.
  **Browser verified** (admin, 1280 px): "Explain why 2 of 3" -> the bank with "Still needs: An explanation", 1 match.
- 2026-11-03: **Content gaps no longer hidden by teachers' activities.** The learning-operations "Content gaps" (a
  curriculum's skills nothing practises) counted a skill as practised if any published activity covered it - including
  a teacher's own, which a platform curriculum's family children never get (PathContentScope, N7). It now uses the
  path's rule: platform content, or the curriculum owner's own. Curriculum publishing already applied it
  (NO_PLAYABLE_ACTIVITY). **Tested**: `CoverageGapScopeIntegrationTests` 1/1 - **fails on the old query** (50 vs 51
  gaps) and passes now; `AdminInsightIntegrationTests` 4/4, readiness 2/2, backend unit 203/203.
- 2026-11-03: **Swept the other activity-by-skill lookups for the same scope mistake** (no change needed): content
  validation (`hasPublishedActivityInScope`), the adaptive engine, the path planner, Smart Review, class practice
  options and assignment (per child's path) and the journey builder's coverage all apply `PathContentScope`; only the
  two admin queries fixed above did not. **A "Still needs" search with nothing left says so as good news** ("Every
  question here is complete in Khmer.") instead of "No question matches these filters". **Tested**:
  `bankNeeds.test.tsx` 2/2, studio + i18n Vitest 227/227.
- 2026-11-03: **Arriving in the child's space** (owner brief: switching between parent and child contexts): the child's
  entrance - greeting, then the painted world 90 ms later - rises in, like the shelf, garden and play pages already do;
  nothing moves under reduced motion. **Tested**: child Vitest 53/53. **Browser verified** (parent -> Sophea's space,
  390 px): `rise-in` on both, the world delayed 0.09 s; reduced motion -> none; no page errors.
- 2026-11-03: **A paste is added once** (failure mode: double submit / retry after a lost response). Both import
  endpoints take an optional `idempotencyKey` (the existing `IdempotencyService`): the same rows again under the key ->
  409 "These rows were added already: they are in the question bank as drafts"; the key reused for other rows -> 409;
  a paste that failed releases its key, so retrying it is not blocked as "in progress". The guard completes in the
  import's own transaction. The Studio sends one key per paste - a new one whenever the rows or skills change or after
  an add. Known limit (as for answers): a failure at commit time itself leaves the key "in progress" until it expires
  (1 day). **Tested**: `QuestionImportOnceIntegrationTests` 3/3 (twice -> once; other rows under the key refused; a failed
  paste retried; an activity gets the row once), import suites 5/5, `questionImport.test.tsx` (same key on an unchanged
  retry, a new key for changed rows), full frontend Vitest 464/464, backend unit 203/203. **Browser verified**
  (regression, teacher 360 px): paste -> add -> publish the ready one, no API error.
- 2026-11-03: **Correction to the R-1 review**: finding ID-2 ("no in-product way to add a second platform admin") was
  wrong and is withdrawn - administrators create staff accounts, administrators included, in Admin -> Staff
  (`POST /admin/staff`, authenticator step-up, audited); the first administrator comes from the startup bootstrap.
  `IDENTITY-PERMISSIONS.md` (how each role is made, the rules for R-1 option B) and the journey review's
  registration row are corrected. No code change.
- 2026-11-03: **A child's tabs move only their content** (parent: overview / progress / settings): the child page claims
  its address with a scoped `Arrival`, as the Studio does, so the child's name and the tabs stay put while the tab's
  content arrives; leaving for the family page moves the page. **Tested**: parent Vitest 72/72. **Browser verified**
  (1280 px): progress and settings -> the tab content only; Family -> the page; no page errors.
- 2026-11-03: **Parent breadcrumbs are 24 px touch targets** (WCAG 2.5.8 by size, not only by spacing). CI's e2e
  accessibility check on "parent: one skill explained" failed once on `f5ecd7a` (target-size: the child's breadcrumb
  link, 42 x 17 px); it doesn't reproduce locally (axe at 0/30/80/600 ms after navigating, scrolled or not). Re-run
  requested to tell flake from regression; the links now meet the size on their own either way. **Tested**: parent
  Vitest 72/72. **Browser verified** (390 px): breadcrumb links 24 px, axe clean on the skill and child pages.
- 2026-11-03: **A new page opens at its top** - root cause of the e2e failure on `f5ecd7a` (reproduced locally with the
  e2e suite against the isolated stack): the router kept the old page's scroll, so the parent's skill page, opened
  from a link far down the progress table, opened half-way down with its breadcrumb under the sticky header (axe:
  target "partially obscured"). `Arrival`'s outermost boundary now resets the scroll on a new page reached by a link -
  not on Back/Forward (the browser restores the place), not for a `#section` link, not for a tab switch inside a
  scope. **Tested**: `arrival.test.tsx` 3/3 (top on a new page; not for a tab, a #section or Back), full frontend
  Vitest 465/465 (`journey.test.tsx` once flaky under full load, passes alone and on re-run); **e2e locally**
  (isolated stack, both projects): golden journeys 8 passed / 1 skipped (admin credentials), whole suite 20 passed,
  23 skipped, 1 failed only on local data (`language.spec` expects the CI seed's "Maths - Junior" curriculum).
- 2026-11-03: **Security sweep, and an authorization regression test for every endpoint.** Measured on the isolated
  stack: every GET with ids (54) and every POST/PUT/PATCH with a schema-shaped body (81) refused a brand-new parent
  naming another family's child, a paid school's class and school, a curriculum and a teacher's own Studio content;
  the 16 id-less endpoints a parent may open are all their own or public, the 37 staff/admin ones refuse. Also
  checked, no change needed: request bodies without `@Valid` are validated in the domain (fixed minute sets, the
  class/year belongs to the school); no response carries a password hash, token, secret or code; every LIKE search
  escapes `%`/`_`/`\`; API docs are off in production; join codes are 8 of 31 symbols from `SecureRandom`; the admin
  authenticator step-up counts failures toward the account lockout and refuses replays. UI crawl of every role's pages
  (54 pages, 360 and 1280 px): no API error, page/console error, serious axe violation or overflow.
  **New**: `AuthorizationSweepIntegrationTests` reads the API's own description, builds a real family (child in a paid
  school's class, an activity started), a school year and term, and a teacher's content, then asserts no read answers
  and no write succeeds for a stranger, that the sweep reaches >= 50 reads and >= 90 writes (a renamed parameter
  can't silently skip endpoints), and that the id-less endpoints a parent opens are exactly an allowlist - so an
  endpoint added later without its guard fails the build. **Proven**: with one guard removed (child daily limit) it
  fails naming that endpoint; restored, 2/2 pass. Not swept (need objects the fixture doesn't make - payments,
  caregivers, device codes, reviews, media, organizations): covered by their own suites.
- 2026-11-03: **Fixed: read-aloud recording was impossible in every production deployment.** Both production header
  sets (`frontend/nginx/security-headers.conf`, `frontend/vercel.json`) sent `Permissions-Policy: microphone=()`, which
  makes the browser refuse `getUserMedia` before asking - the Studio's voice recorder (read-aloud for pre-readers)
  only worked in local dev (no such header), and a teacher would have been told to "allow it in the browser's
  settings", which can't override a server policy. Now `microphone=(self)`: the app's own pages may ask, nothing
  embedded may. **Proven** in Chromium with a fake microphone: the old header -> `NotAllowedError`, the new -> records.
  Security review notes (no change): sign-in and recovery are enumeration-safe (same answer, dummy password hash for
  unknown emails - timing medians 64 vs 60 ms); CSP is strict (`script-src 'self'`, no inline, `frame-ancestors
  'none'`), HSTS behind TLS; the refresh token in `localStorage` is a documented choice mitigated by rotation and reuse
  detection with the access token in memory. **For the owner (S-ENUM-1)**: registration answers 409 "An account already
  exists for this email address", so it reveals which emails have accounts (rate limited per network); closing it
  means always answering "check your email" and emailing the existing owner - a change to the sign-up experience.
- 2026-11-03: **Hardening: request bodies are capped in the backend too** (resource exhaustion). The nginx front caps
  /api bodies at 1 MB (6 MB uploads), but the hosted deployment (Vercel -> Railway) has no nginx, and Spring MVC reads a
  JSON body whole into memory before validation - one oversized request, even to the unauthenticated sign-in (the rate
  limiter counts requests, not bytes), could exhaust a small container. `RequestSizeLimitFilter`: a non-multipart
  body over `platform.http.max-body-bytes` (1 MB, as nginx) -> 413 problem JSON in the reader's language, before it is
  read; a body without a length (chunked) is read only to the limit, then refused; uploads keep their multipart
  limits. **Tested**: `RequestSizeLimitIntegrationTests` 2/2 (2 MB sign-in body -> 413 in English and Khmer; chunked ->
  refused; a small request -> the usual 401; a 1.5 MB picture upload -> 201), media/import/sweep integration 14/14,
  backend unit 203/203. Checked, no change: media URLs are relative (same origin under the CSP); the hosted
  deployment proxies /api through Vercel, so `connect-src 'self'` holds.
- 2026-11-03: **Fixed: "Play again" on a finished book failed once the activity was revised** (found by crawling the
  child's space: `POST /children/{id}/activity-attempts` -> 404). The shelf linked to the version the child played;
  an author's revision retires it, and a new attempt can't start on a retired version, so the child tapped a lit book
  and got an error. The journey step now also says what to play again - `playAgainVersionId`, the activity's current
  published version (none when the activity is archived or nothing is published) - while keeping the version played
  for its record; the shelf links to it and hides "Play again" when there is nothing to play. No learning rule changes:
  free practice was already per activity, outside the path (X24, OD-10). A server that doesn't send the field (older)
  keeps the previous link. **Tested**: `PlayAgainVersionIntegrationTests` 1/1 (finished -> revised: the step keeps v1,
  names v2; an attempt on v1 is refused, on v2 starts; archived -> none), journey API 8/8, golden journey, sweep 2/2,
  `shelfReplay.test.tsx` (revised, none, older server), full frontend Vitest 466/466, backend unit 203/203. **Browser
  verified** (Sophea, 390 px): "Play again" on Addition Recognition Practice -> "Playing it again" on its current
  version, no API error (it was a 404 before).
- 2026-11-03: **Fixed a regression from the "play again" fix (CI red on `c2e6cb2`)**: `ReadQueryScalingIntegrationTests`
  showed the enrollments read growing with the child's history (44 statements after 1 activity, 49 after 6) - the new
  "play again" lookup ran once per finished activity. It now loads every activity's current published version in one
  query inside the journey's batch preload, and the journey preloads its path before building steps. **Tested**: the
  scaling test passes again; the **full backend integration suite 536/536** locally (3 proxy tests hit the local
  Postgres connection limit in the full run - "too many clients" - and pass alone), backend unit 203/203. Lesson kept:
  read-path changes get the full integration suite before a push, not only the related suites.
- 2026-11-03: **F-IDEM-1 fixed: a child is added once.** Adding a child had no duplicate guard: a lost response then a
  second tap created the child twice (and took a second place of the family's plan). The add-child form now sends an
  `Idempotency-Key` header (already in the CORS allow-list) - one key per child as typed, renewed when the name or
  birthday changes; the server, through the existing `IdempotencyService`, answers the same request again with the child
  the first one added (found as the family's latest with that name and birthday, inside the key's day), refuses the
  key reused for a different child (409) or over 100 characters (400), and starts auto-enrolment only for the request
  that added the child. Header rather than a body field so the request body - and the existing add-child test that
  asserts it - is unchanged. Without a key, as before. **Tested**: `AddChildOnceIntegrationTests` 3/3 (same key twice ->
  one child; two simultaneous taps -> one child, no 500; another key -> another child; reused key 409; overlong 400; no
  key unchanged), `childCreate.test.ts` (header sent, body unchanged), full backend integration 539/539 (the 3 proxy
  tests again hit the local connection limit and pass alone), full frontend 467/467 (`media.test.tsx` times out under
  full load, passes alone), backend unit 203/203. **Browser verified**: a fresh sample parent adds a child; the request
  carries the key; replaying it returns the same child; the family has one.
- 2026-11-03: **Checkpoint and review: security, privacy and reliability.** `docs/SECURITY-AND-RELIABILITY.md`
  (linked from the journey review): assets and who must not reach them; every control by threat (identity and
  sessions, authorization and privacy, input/resources/web surface) with its evidence and level - the tests that
  assert it, or the runtime probe, or inspection only; failure modes (double submit, races, half-way failures, lost
  refresh responses, revisions under a child, growing history, a slow server) with evidence; measured performance; open
  items S-ENUM-1 (owner), TD-15 (per-instance rate limits), SR-RT, SR-DOCS, SR-KEY. The canonical progress record gets
  a dated 2026-11-03 state and next work. No code change.
- 2026-11-03: **Every question type checked as a child plays it, and the answer feedback reads cleanly to a screen
  reader.** Seven one-question sample activities (choice, true/false, number, typed word, matching, ordering, sorting)
  authored on the dev stack and played at 390 px: axe clean on every question and on the wrong-answer feedback of the
  four simple types, no overflow, no API error. **Fixed**: the feedback panel was one live region containing its own
  continue button, so the announcement ran "...good try! The answer is 4. Let's keep going! Finish Why..." - while focus
  also moved to that button. The live region now holds the verdict, the answer and why; the button follows it (focus
  still moves there), and sits after the explanation on every width - read what happened and why, then go on (on
  phones it used to sit above the explanation). **Tested**: child Vitest 54/54 (incl. the protected `mistakeHelp`
  test: the status still holds the hint). **Browser verified**: live text without "Finish"; layout screenshot.
- 2026-11-03: **M13 readiness docs brought up to date with today's hardening.** The body cap is a named setting like
  the others (`HTTP_MAX_BODY_BYTES`, default 1 MB, in `application.yaml`) and is listed in `DEPLOYMENT.md`'s tunables
  (the only cap on the hosted path, which has no nginx). `docs/DEPLOY-VERCEL-RAILWAY.md`'s staging checks gain
  **10. read-aloud recording** (the browser asks for the microphone and the recording plays back - blocked by the old
  `microphone=()` header) and **11. oversized requests refused** (a 2 MB sign-in body answers 413; an ordinary sign-in
  still works); its Flyway note now says the latest version (V85) instead of V62. **Tested**:
  `RequestSizeLimitIntegrationTests` 2/2 and `ConfigurationContractTests` 61/61 with the setting in place. CI: last
  full green **`6f12c98`** (backend, frontend, deployment, e2e) - confirms `c910f3a`'s scaling fix and F-IDEM-1.
- 2026-11-03: **The answer-feedback restructure above is reverted (finding A11Y-1 for the owner).** The protected
  `m2.test.tsx` asserts the Finish button sits *inside* the feedback's status region
  (`within(verdict).getByRole("button", { name: /Finish/ })`), so moving it out is a deliberate behaviour change that
  needs the owner's approval to update that test - not a change to make by editing it. `AnswerVerdict.tsx` is back to
  `6f12c98`. **A11Y-1 (open, Low)**: the live region announces its own button label after the explanation ("...Let's
  keep going! Finish Why..."); the fix is ready (verdict, answer and why in the region; the button after it, focus
  unchanged) once `m2.test.tsx` may follow it. **Tested**: marketing, child and i18n Vitest 205/205.
- 2026-11-03: **F1d - the landing page says what schools and parents now get.** It said less than the product does:
  schools gain two capabilities - the questions a class finds hardest on a skill (what to teach again, beside who needs
  practice) and many questions written at once (paste from a spreadsheet, English and Khmer, check then publish) - and
  the parents section says that after an activity they see the questions their child missed, with the right answer
  and, where the author wrote one, why. Each line describes shipped behaviour only. Khmer drafted (PD-14 review).
  **Tested**: marketing Vitest. **Browser verified**: 360 px in English and Khmer - 8 school items, no overflow, axe
  clean (serious/critical).
- 2026-11-04: **Phase 1 "unsaved-guard for dialogs" - done.** Page forms in the Studio were guarded on 2026-10-29/30
  and the Studio's Manage dialog asked before closing; three dialogs still dropped typed text on a stray Escape, a
  click beside the panel, or the close button: **rename a class**, **rename a school** (principal) and **a media
  item's title and description** (Studio). Each now asks first ("Your changes aren't saved yet. Close and lose
  them?" for the school dialogs, Studio's existing wording for the media dialog); Stay keeps the dialog and the text.
  A save counts at once - closing right after it doesn't ask, even before the page has reloaded its copy of the name.
  The 6-digit step-up code dialog is left as it is (nothing worth keeping). **Tested**: new `manageUnsaved.test.tsx`
  (school, 3) and `mediaUnsaved.test.tsx` (2); school and Studio Vitest 98/98; tsc; eslint. **Browser verified**
  (Chromium): principal at 360 px - typed school name, Escape -> prompt, Stay kept the text, Leave closed; admin at
  1280 px - typed media title, backdrop click -> prompt, Stay kept, Leave closed. Khmer prompt drafted (PD-14).
- 2026-11-04: **Phase 1 acceptance "a full session plays on a phone over a mobile connection" - measured, and the
  child's space gains its main landmark.** On the production build (gzipped), at 390 px with a "Fast 3G"-like network
  (563 ms round trip, 1.6 Mb/s) and a 4x slower CPU, a child's whole session plays without an error: child home 6.3 s
  on first visit (~370 KB, 123 KB cached), tapping a book to its first question 2.6 s, answer to feedback 1.3 s, 578 KB
  in all (figures in `docs/SECURITY-AND-RELIABILITY.md` section 3). Nothing changed for speed: the slowest step is
  round trips that depend on each other, not size. **Fixed** (found while measuring): the child's space had no `main`
  landmark - the page sat in a plain `div` after the space's controls, so a screen reader couldn't jump past them to
  the activity, the shelf or the home (WCAG 2.4.1). `ChildSpaceLayout` now holds the page in `<main>`. **Tested**: new
  `childLandmarks.test.tsx` (one main, holding the page, the controls outside it; with and without the focus header);
  child Vitest 56/56. **Browser verified**: axe `landmark-one-main`, `region`, `bypass`, duplicate/top-level main on 17
  pages across the public site, parent, child (home, shelf, buddy, a question), teacher, principal and admin - all
  pass.
- 2026-11-04: **The connection drops while a child answers - checked, no change needed.** On the production build at
  390 px: a number question answered with the network cut - the child reads "We couldn't send that answer. Please try
  again.", the answer stays chosen, nothing reaches the server; back online, "Check" sends it and it is recorded once
  (one 201, one answer row). An attempt whose questions are all answered is completed when reopened and shows its
  result (as designed). **Runtime verified** (Chromium, isolated stack); no automated test added (the existing
  resume and failure tests cover the code paths).
- 2026-11-04: **Sign-in no longer blames the details when the server fails.** Any refusal other than a rate limit, a
  lockout or a field error fell back to "Couldn't log you in with those details." - including a host's 502/503 while
  the backend restarts or deploys (no problem body). A parent with a working password would be sent to reset it. A
  server error (5xx) now reads "Something went wrong. Please try again."; a 401 still shows the server's own words (or
  the wrong-details line when it gives none), so account enumeration protection is unchanged. Registration ("Couldn't
  create that account.") and reset are left as they are - neither points the person the wrong way. **Tested**: new
  `loginErrors.test.tsx` (502, 503, 500 -> something went wrong; 401 with and without words); auth Vitest 11/11.
  **Browser verified** on the production build at 360 px, English and Khmer: a 503 from the host -> the new line; a
  real wrong password -> "Invalid email or password".
- 2026-11-04: **Page titles and the account pages checked.** Correction to the landmark entry above: its "account"
  page was the not-found page (the account lives at `/<role>/account`). Checked now: every role's account page at
  360 and 1280 px - axe at every impact level incl. best practice clean, no overflow. Every page visited across the
  five roles and the public site has its own title naming the place (WCAG 2.4.2): "Sophea's path", "Grade 1 A",
  "Media library"... no two alike. Full frontend suite **477/477**, tsc clean, eslint 0 errors (8 existing
  fast-refresh warnings).
- 2026-11-04: **Keyboard focus and reduced motion checked at runtime** (production build, Chromium 1280 px): tabbing
  through the landing page, sign-in, the parent home, a child's page for the parent, the child home and a sorting
  question - 78 controls - every one looks different when focused than at rest (outline, ring or colour); with
  `prefers-reduced-motion: reduce`, no animation is running on any of those pages once loaded. No change needed.
- 2026-11-04: **Regression test: an answer checked while offline** - new `e2e/answer-offline.spec.ts` (desktop and
  phone): a new family's child, on the seeded Maths path, checks an answer with the connection cut - told it wasn't
  sent, answer kept, axe clean on that state; back online, the same Check sends it, feedback shows, exactly one
  answer recorded (one 201). **Tested** locally against the production build: 2/2.
- 2026-11-04: **Regression test: finishing an activity while offline** - a second test in `e2e/answer-offline.spec.ts`:
  every question answered, the connection cut as the child taps Finish - "We couldn't save your activity just now."
  with Try again, axe clean; back online, Try again resumes the server's attempt and shows the completion with every
  answer counted once. **Tested** locally against the production build: desktop and phone, 4/4 in the file.
- 2026-11-04: **A child's date of birth: a mistyped year is refused when the child is added, and the reason reads in
  Khmer.** Correcting a date of birth already refused dates before 1900 ("Enter the child's real date of birth"), but
  adding a child accepted any past date: "0019-03-14" (a typo for 2019) was stored, making a 2,000-year-old child whose
  age then decides which paths fit. `Child.enroll` now applies the same floor (no new rule: the correction's own). Both
  birth-date sentences are `Assert`s, which the Khmer coverage test doesn't scan (it reads `new ...Exception("...")`
  only), so a Khmer parent got the generic "This request is invalid..." instead of the reason: Khmer added to
  `ServerText` for both, and the new `ServerTextAssertCoverageTests` scans asserted sentences so the next one can't
  miss it (it found exactly these two). A browser `min` on the date fields was tried and dropped: the protected
  `childEdit.test.tsx` expects the server's own words for an impossible date, which also read in the parent's
  language where a browser's native message may not. **Tested**: new `ChildBirthDateTests` (2),
  `ServerTextAssertCoverageTests` (1), `ServerTextCoverageTests` 3/3, parent Vitest 72/72. **Browser verified**
  (production build + rebuilt backend, 360 px): year 0019 when adding a child -> 400, "Enter the child's real date of
  birth" in English and "សូមបញ្ចូលថ្ងៃខែឆ្នាំកំណើតពិតប្រាកដរបស់កុមារ" in Khmer (PD-14 draft); no child added. Seed
  accounts restored to English afterwards.
- 2026-11-04: **Every page works at 320 px (WCAG 1.4.10), in English and Khmer.** Earlier sweeps ran at 360 px; many
  budget phones are narrower. A 320 px check of 23 pages per language (public site, parent, child incl. sorting and
  matching questions, teacher, principal, admin, each role's account) found two: the account page's settings rows
  ("Your family's day": the time-zone picker's long option made the row wider than its card, which cut it off) and
  the principal's school picker (a long school name pushed the page 20 px sideways). Cause in both: a box that sizes
  to its content. Fixed: the settings row has `grid-cols-1` below its breakpoint; the school picker and its label may
  shrink (`min-w-0 max-w-full`). The same pattern - a grid with columns only from a breakpoint up - was in 14 more
  places (landing sections, payments, storage, class and school lists): each now also says `grid-cols-1`, which
  changes nothing at wider widths (checked: the landing's grids keep their 2-5 columns at 1280 px). New
  `components/ui/gridColumns.test.ts` reads every screen's source so a new such grid fails the suite. **Tested**: full
  frontend 479/479 (incl. the new 2), tsc. **Browser verified** (production build, 320 px, English and Khmer): 23 + 23
  pages, no sideways scroll, nothing cut off. Seed accounts back on English.
- 2026-11-04: **320 px crawl of every role, English and Khmer - clean.** Each seed role's every page reachable from
  its navigation and one level of in-page links (parent 7, teacher 14, principal 9, administrator 24 = 54 pages per
  language) at 320 px on the production build: no sideways scroll, no element cut off outside a scrolling region,
  no serious/critical axe violation, no API, page or console error.
- 2026-11-04: **Text spacing (WCAG 1.4.12) checked, and the child's shelf fixed on narrow phones.** With the spacing a
  reader may set (line height 1.5, letter 0.12em, word 0.16em, paragraphs 2em) on 17 key pages at 360 px, one place
  clipped: the shelf's book cards - their "Play again" pill never wraps, and the card hides what doesn't fit. Looking
  closer, the pill didn't fit even at normal spacing at 320 px (about 100 px of label in 74 px of card, its end cut
  off), and two books a row left a title 74 px - "Recognition" alone is wider. Fixed: the shelf shows one book a row
  below 360 px (two from 360, as before; 3/4 wider); the pill may wrap onto two centred lines within its card (it
  does at 360-389 px, a big, clear button); the status ("Lit · Review") wraps beside its tick instead of leaving the
  tick alone; titles hyphenate where the language allows. **Tested**: child Vitest 56/56 (incl. the protected shelf
  tests), the grid guard. **Browser verified** (production build): screenshots at 320, 360 and 390 px (whole words,
  nothing cut off); the text-spacing check now finds nothing on any of the 17 pages. Note: the 320 px crawl above
  compares elements with the screen's edge only, so a card cutting off its own content can still slip past it.
- 2026-11-04: **The 320/360 px crawl now also catches content a card cuts off - one more fix.** The crawl's check
  compares every text element with any ancestor that hides its overflow (not only the screen's edge), allowing
  regions that scroll on purpose (tables, the Studio's tabs) and deliberate ellipses. It found one more: the
  principal's class list at 320 px, where a long academic-year name widened the class's year picker beyond its
  card. Fixed like the school picker (label and picker may shrink; the word "Academic year" keeps its width).
  **Tested**: principal Vitest 22/22. **Browser verified** (production build): every role's pages (54) at 320 and
  360 px in English and Khmer, plus the child's space (5 pages reachable from the home) at 320 and 360 - no
  sideways scroll, nothing cut off, no serious axe violation, no error.
- 2026-11-04: **Touch targets in the child's space: every control at least 44 x 44 px.** Checked at 360 px on the
  home, shelf, buddy chooser and in each question type (introduction and question): the language switch was 42 px
  wide (the word "ខ្មែរ" is short) - now at least 44. Every other control already met it. Also crawled every role
  and the child's space at 640 px (200% zoom of a laptop, where the `sm:` layouts start) and 768 px: nothing cut off,
  no sideways scroll, no serious axe violation. **Tested**: child Vitest 56/56. **Browser verified** (production
  build).
- 2026-11-04: **Focus through an activity, by keyboard - checked, no change needed.** A new sample family's child on
  the seeded Maths path, keys only: after "Let's go" focus is on the first question's answer; after Check, on the
  button that goes on (Next / Finish); after Next, on the next question's answer - never dropped to the page. Emails
  checked by reading `AccountRecoveryService`: each is written in its recipient's interface language (INSPECTED).
- 2026-11-04: **The landing page's first view on a phone is 90 KB lighter.** A family's first visit at 390 px
  downloaded 600 KB before scrolling; the large-screen hero (the parent's home in a browser frame, the child's home in
  a phone) is hidden below `lg` yet its pictures were marked eager, so every phone fetched the 92 KB parent screenshot
  it never shows. They now load lazily - a hidden lazy picture is never fetched; on a large screen they are in the
  first view and load at once (both shown 232 ms after navigation at 1280 px). Phone first view: **508 KB** (to the
  end of the page 760 KB, was 851). **Tested**: marketing Vitest. **Browser verified** (production build).
- 2026-11-04: **Opening the app straight at a page starts that page's code at once.** Measured cold loads showed the
  page's code (each area - family, child's space, teacher, principal, admin - is its own download) requested only
  after the session check came back: a round trip in a row for every reload, bookmark and, above all, a child's own
  device (M10a), which opens straight into `/learn/...` each time. `preloadAreaFor` (`routes/areas.ts`, called in
  `main.tsx`) starts the area of the opening address beside the session check; the route's lazy import then finds it
  loaded (a failed early download changes nothing - the route loads it again with its own error handling). On the
  slow-3G phone profile: a cold reload into an activity **5.5 s -> 4.85 s**, the child home 6.4 -> 5.8 s. **Tested**:
  new `routes/areas.test.ts` (addresses to areas, inherited names like "/constructor" are no area, the download
  starts and a second call is harmless); full frontend 493/493, tsc, eslint. **Runtime verified** (production build,
  throttled, twice): the child-space code and the session refresh now go out together.
- 2026-11-04: **Tried and dropped: fetching an activity beside its attempt start.** Tapping a book costs three round
  trips in a row (next activity -> start the attempt -> its answers, which need the attempt's id); asking for the
  activity beside the start moved it, not the chain (2.61 -> 2.56 s on slow 3G, noise), so the change was reverted.
  Recorded idea **PERF-1** (not built): the attempt start could return the answers already recorded on it - one round
  trip less per activity start (about 0.6 s on slow 3G) - an additive change to the attempt contract (Battle 2A's
  path), worth doing with its own integration tests when activity start speed matters more than now.
- 2026-11-04: **A visitor's first minute on a slow phone, and the e2e run on the final build.** Slow-3G phone profile,
  production build: the landing page's headline shows 2.5 s after a cold open (138 KB), the page settles at 5.4 s;
  "Try a lesson" opens in under 0.1 s. Full local e2e on the final build: 24 passed, 23 skipped (administrator
  credentials not set locally), 1 failed - `language.spec`, which looks for "Maths - Junior" on a Khmer page: the
  local data gives that curriculum a Khmer name ("គណិតវិទ្យា - កម្រិតដំបូង", checked in the database) that CI's seed
  doesn't have; CI ran it green on `6f12c98`.
- 2026-11-04: **Text spacing across every role, English and Khmer - clean.** The crawl of every role's pages (54) at
  360 px with the reader's spacing applied (WCAG 1.4.12) finds nothing cut off and no sideways scroll, in English and
  in Khmer (whose clusters and longer words are the harder case). Pushed `6f12c98..6507a60` (27 commits) for CI.
- 2026-11-04: **CI fully green on `6507a60`** (run 41778528827): backend unit + integration (Testcontainers),
  frontend (lint, types, tests, build, audit), deployment files, end-to-end journeys and accessibility on the Docker
  stack - including the new offline e2e tests. Everything built on 2026-11-04 through `6507a60` is CI-verified.
- 2026-11-04: **PERF-1 built: starting an activity is one round trip shorter.** The attempt start
  (`POST /children/{id}/activity-attempts`) now answers with the answers already recorded on the attempt
  (`StartedAttemptResponse.answers`: empty for a new attempt, exactly what `GET /activity-attempts/{id}/answers`
  returns for a resumed one - Battle 2A unchanged). Additive: every earlier field is as it was. The players use them
  (`answersOf`: falls back to asking when a start has none, e.g. an older server during a deploy) and so ask for the
  activity beside the start instead of after it - the child may read it because it is on their path or allowed as
  practice (`LearnerContentReadGuard`), attempt or not. Slow-3G phone, production build: **tapping a book 2.6 ->
  2.05 s**; a cold reload into an activity 4.85 -> 4.36 s (5.5 s this morning). **Tested**: new
  `StartAttemptAnswersIntegrationTests` (new attempt: none; resumed: the same attempt, its answer, identical to the
  answers endpoint; another family can't start and learns nothing), `PlayAgainVersionIntegrationTests`,
  `ReadQueryScalingIntegrationTests`, `AuthorizationSweepIntegrationTests`; backend unit 206/206; new
  `startAnswers.test.tsx` (answers carried -> no second request, resumes at question 2; without -> asks, same
  result); full frontend 495/495, tsc, eslint; local e2e golden, offline, journey-stop, review and accessibility
  journeys 18 passed / 7 skipped (admin credentials). **Runtime verified** (rebuilt backend + production build).
- 2026-11-04: **Every page when the server is down - checked, no change needed.** Each role's pages were opened
  with every data read failing (503 with no body, as a host answers while the backend restarts; sign-in state
  still answering): 27 pages across parent, teacher, principal and administrator each say "That didn't load ·
  Something went wrong" with Try again - no blank page, no crash. (The administrator's accounts page reads nothing
  until a search, so it has nothing to fail.) Coverage note: pages linked only from inside a loaded page were not
  reached in this run, because links are read from the failed page. **Runtime verified** (production build, 390 px).
- 2026-11-04: **Adding a child: a plan that didn't load is no longer an empty plan with "Finish".** Found by the
  server-down crawl, now with links read from each page as it loads normally (54 pages; all others say "That didn't
  load" with Try again). The last step of adding a child re-reads the subjects chosen; when that read failed (a
  network blip between two screens) it showed the plan's heading, no subjects, no message and **Finish** - finishing
  left the child with none of the chosen subjects started, unannounced (CLAUDE.md §8). It now says it didn't load,
  with Try again, and offers only Back until the plan is there (also while it loads). **Tested**: new
  `addChildPlanError.test.tsx` (the read fails once: alert, no Finish, no Start; Try again: the plan and Start);
  parent Vitest 72/72 incl. the protected `addChild.test.tsx`. **Browser verified** (production build, 360 px, a new
  sample family): plan read failed -> "That didn't load · Something went wrong · Try again", no Finish; Try again,
  Start -> the child's page shows "Up next".
- 2026-11-04: **Reads whose failure looked like an answer - family screens fixed.** A scan for `useAsync` reads whose
  error a screen never looks at found 41; read one by one, most are optional extras (a title's name, a meter, a
  tip). Two on family screens could mislead, now fixed: **the family's payments** - when its payments or the ways of
  paying couldn't be read, a payment already waiting (with the reference the family quotes) looked absent and the
  plan choices unlocked; now it says so with Try again and offers no plan until both are known (the server would
  refuse a second waiting payment anyway). **The family home's "What the answers show"** said "Loading..." for ever
  when the skills couldn't be read; now "What the answers show couldn't be loaded." (Khmer drafted, PD-14).
  **Tested**: new `paymentsUnknown.test.tsx` (payments fail: alert, Choose disabled; Try again: the waiting payment and
  its reference; ways of paying fail: alert, nothing to choose), `homeSkillsFailed.test.tsx`; parent + i18n Vitest
  221/221 incl. the protected payment and family-home tests.
- 2026-11-04: **School billing: a part that couldn't be read says so.** Same scan: the school's ways to pay
  (`SchoolRenew`, the only way a school waiting to pay gets paid) and its payments (`SchoolInvoices`, where a
  waiting payment shows its reference) rendered nothing when their read failed. Each now says what couldn't be loaded
  with Try again, as a line within the card (the app's pattern for part of a page; an alert box there would also
  compete with the page's own alerts - the protected `principal.test.tsx` expects the limit reason to be the one
  alert, and passes). Khmer drafted (PD-14). **Tested**: new `schoolBillingFailed.test.tsx` (payments fail -> the line;
  Try again -> the waiting payment's reference; ways to pay fail -> the line; Try again -> the plan to choose); school,
  principal and i18n Vitest 181/181.
- 2026-11-04: **A class's "assign a curriculum" says when its choices couldn't be read.** Same scan: when the
  curricula a principal or teacher may assign couldn't be read, the assign control simply wasn't there - as if there
  were nothing to assign. Now "The curricula to assign couldn't be loaded." with Try again (Khmer drafted). The rest of
  the 41 were read and left as they are, each for a reason: optional extras (a name in a title, a storage meter, a
  plan summary line with a link to the full plan page, the first-steps card, a skill's movement strip), reads the
  page already answers elsewhere, and two documented as deliberate (a teacher's school invitations: "the class list
  below still works"; a family's caregivers: nothing shows when there are none). **Tested**: new
  `classCatalogFailed.test.tsx`; school, teacher, principal and i18n Vitest 185/185.
- 2026-11-04: **The administrator's staff page says when principal applications couldn't be read** (same scan):
  schools waiting for their principal's approval looked like none. Now "Principal applications couldn't be loaded."
  with Try again (Khmer drafted). **Tested**: admin + i18n Vitest 253/253, tsc, eslint. Not browser verified.
- 2026-11-04: **CI fully green on `7fc6de4`** (run 41784095600): backend unit + integration, frontend, deployment
  files, end-to-end and accessibility - PERF-1 and the failed-read fixes included. `b70302a` (staff page) pushed
  after it; its CI was not checked in this window.

## Owner direction 2026-11-04 (supersedes the order of work above; the roadmap stays the source of truth)

**Priority order:** 1. planned and approved features, built completely (backend, frontend, data, integration, tests,
the real journey) -> 2. product experience: transitions, movement, animation, usability (high priority) -> 3.
remaining defects (anything critical at once) -> 4. simplification (without removing useful capability).

**Decisions recorded today (owner-approved):**
- **STREAK-1 - learning streaks: approved, keep and build.** Replaces the earlier recommendation to remove the
  dormant streak backend, and is the deliberate product decision CLAUDE.md §19 requires for streaks. Wanted: a visible
  streak, an animated flame (flicker, glow, reacts when the streak grows), milestone celebrations, the same
  presentation across the child's space, built on the existing backend and learning records. Rules: the count is
  real learning activity, never fabricated; inspect the existing streak backend before changing any rule; no timers,
  lives, penalties or guilt ("you'll lose") wording; encouraging, not stressful; reduced motion respected. The landing
  page's "No points to chase, no streaks to lose" line must change with it (it would no longer be true).
- **EXP-1 - motion and transitions across the product:** pages and sections, dialogs/drawers/menus, between
  learning environments, starting and finishing an activity, learning-state changes, parent <-> child; character
  reactions, environmental motion, responsive objects, progression and reward animation, animated progress. Every
  animation must explain, give feedback, show progress or delight; mobile performance and reduced motion kept.
- **WORLD-1 - a world for the child, not one scenery:** several distinct, connected learning environments (e.g.
  rivers, rice fields, forest, village, gardens), landmarks, vegetation, atmosphere, depth/parallax, connected scene
  transitions, a sense of exploration and progression; a reusable environment system (scenes, layers, animation).
  Child experience only - parent, teacher and admin screens keep their professional design.
- **LAND-1 - landing page:** fix the top composition at its cause (navigation clipped, first heading line cut or
  overlapped, hero too close to the top, crowded device mockups, characters/scenery not integrated, no transition into
  the next section; header height, sticky behaviour, safe area, line height, overflow, breakpoints) - never by hiding
  overflow. **Every** visible landing text editable by authorized staff in English and Khmer without code: navigation,
  hero, CTAs, every section heading and description, features, benefits, statistics, testimonials, **FAQs** (this
  settles L-3 for the landing page), labels, footer; preview before publishing where practical; repeatable items
  where the roadmap supports them; layout holds with longer and shorter text. No heavy CMS.
- **FLEX-1 - no fixed three subjects:** layouts, navigation, progress summaries and landing copy must not assume
  exactly Khmer, English and Maths; more subjects and activity types through the existing data-driven model (overlaps
  SUB-1: landing subjects from published curricula).

**Not approved by this direction** (unchanged, still the owner's): scoring and mastery (D-18, D-20, AE-2, SR-1),
accounts and roles (R-1, S-ENUM-1), consent and privacy, subscriptions and payments (P-1, SB1-SB9), storage provider
(D-06/D-12), A11Y-1, N-1, A-1, C-1, school management S1-S6/B1-B5, D-19 content, PD-14 Khmer review.

**CHECKPOINT - exact next work (next usage window):**
1. **STREAK-1**: read the dormant `streak` package (backend: entity, service, endpoint, tests) and how it counts days;
   wire it to real learning records if not already; child-space streak with an animated flame, growth reaction and
   milestones; integration + frontend tests; browser check incl. reduced motion; change the landing line.
2. **LAND-1 top section**: reproduce at 360/768/1024/1280/1440 px, find the cause, fix; then inventory every landing
   string not yet in the site-copy model (F1a made most words editable - list the rest: FAQ, testimonials, statistics,
   repeatable features) and extend the existing model and its admin page, EN/KM, with preview.
3. **WORLD-1 + EXP-1**: design the environment system on top of `features/world/PaintedWorld` (scenes per learning
   area, layers, parallax, ambient motion), connected scene transitions, activity start/finish transitions.
4. **FLEX-1** audit alongside: no "three subjects" assumptions in child, parent and landing.
Then defects, then simplification. Push at the end of the window; CI once.
- 2026-11-04: **STREAK-1 slice 1 built - the learning streak with a living flame.** Backend (inspected, three
  defects fixed, the rule itself unchanged - consecutive learning days, best kept): the day was the **UTC** date (a
  child in Cambodia learning before 7 am counted for the day before) - now the child's learning day, the same zone the
  daily limit uses (`DailyLearningTimeService.learningDayZone`); the read returned a **stale** count after missed days
  - now `currentStreak` is as of today (0 after a missed day, best kept) with `learnedToday`; the read was family-only
  - now whoever may open the child's space (`ChildAccess.requireLearner`; a view-only caregiver still refused, as the
  protected `CaregiverIntegrationTests` requires). The progress endpoint uses the same live count. Frontend:
  `StreakFlame` (layered body and core that sway from the base, breathing glow, rising embers; a grow swell; an unlit
  warm ember when there is no streak - never a broken one); `StreakChip` in the child's header (days, swells when the
  number rises); `StreakMoment` on both completion screens (path and free practice) - "You lit your flame!", "Your
  flame grew - N days in a row!", milestones at 3, 7, 14, 21, 30, 50, 100, 200, 365 - from the server's streak before
  and after the activity, never guessed. No timers, lives, penalties or "don't lose it" words; reduced motion: still.
  Public copy that said "no streaks" now says what the streak is (landing, trust strip; EN + KM drafts, PD-14).
  **Tested**: new `LearnerStreakServiceTests` (Cambodia day boundary; alive today/yesterday, 0 after a gap, best
  kept; none yet), `StreakIntegrationTests` (finish -> 1, today counted; a second activity the same day stays 1;
  another family 404), `CaregiverIntegrationTests` and `CrossFamilyAccessSweepIntegrationTests` still pass; new
  `streak.test.tsx` (growth rule, the moment, the chip lit and unlit); child + marketing + i18n Vitest 209/209.
  **Browser verified** (production build, 390 px, a new sample family): unlit before, "You lit your flame!" after the
  first activity, header "1-day learning streak"; with reduced motion no flame animation runs. Next slice: milestone
  celebration on the home and the shelf (bigger moment), and a parent-side mention (read-only).
- 2026-11-04: **LAND-1 slice 1 - the landing hero's composition fixed at its causes.** Measured at 320-1920 px and at
  short laptop windows (1366 x 657, 1536 x 730, 1280 x 600) in English and Khmer: (1) the heading was a fixed 72 px from
  `lg` - on a ~650 px-tall window four lines pushed the buttons below the fold: it is now the smaller of 72 px and
  9.4% of the window's height (never under 48 px), with the top padding scaled the same way; (2) the phone mockup
  (44% wide) covered half the dashboard and the caption: now 36%, set further left, so both screens read; (3) the
  phone's capture ended mid-word ("My subjects") at the frame - its screen now fades out; (4) the otter and frog stood
  in the background scenery at a fixed spot, apart from everything on wide screens - they now stand at the foot of
  the screens, presenting them; (5) the caption sat where the phone hangs - the screens and buddies have their own
  box and the caption follows it; (6) the hero stopped at a hard edge - its scene eases into the page. Khmer's
  heading line height (1.4) was already applied (checked: the language rule outranks the utility). Not reproduced:
  a clipped navigation bar (header 73 px, sticky, nothing cut at any measured size) - the owner's screenshot did not
  arrive; to recheck with it. **Tested**: marketing Vitest. **Browser verified** (production build): 12 sizes x 2
  languages, no sideways scroll, axe clean; screenshots at 1024, 1280, 1366 x 657 and 1920.
- 2026-11-04: **A11Y-1 done (owner approved updating existing tests where an approved change needs it).** The
  answer feedback's live region now holds what happened and why - the verdict, the answer, the explanation - and its
  continue button follows it (focus still moves there), so a screen reader no longer reads the button's label into
  the announcement. Restores `f610025`'s `AnswerVerdict.tsx`; `m2.test.tsx` now asserts the button is outside the
  region. **Tested**: marketing + child Vitest 69/69.
- 2026-11-04: **P-1 done (LAND-1): the landing page's plans are the plan records.** New public, read-only
  `GET /api/v1/site/plans` (`PublicPlans`): what is on offer now - each audience's default plan and the plans people
  choose themselves, active and inside their offer dates (the rule a family's own plan page uses) - with name and
  description in the reader's language (V82 Khmer where written), price or nothing to pay, and what it includes; no
  internal fields. The plans section shows those records (name, price, limits worded as on the family's plan page);
  the words around them - title, lead, sheet names, points, notes, CTAs - stay editable copy. An audience with nothing
  on offer (schools arranged with the team) keeps the copy's own wording; when the plans can't be read the section
  says "Prices couldn't be loaded just now" and shows no price. A price changed in Admin -> Plans is the price the
  landing shows. The authorization sweep's public allowlist gained the endpoint (owner-approved test update).
  **Tested**: new `PublicPlansIntegrationTests` (anyone reads it; an active self-service plan with its price; a draft,
  an admin-assigned plan and a retired one absent; the default shown; no internal fields),
  `AuthorizationSweepIntegrationTests` 2/2; new `landingPlans.test.tsx` (records shown with price and limits; no school
  offer -> the copy; read fails -> no price); marketing + i18n Vitest 151/151. **Browser verified** (production build,
  1280 px, EN/KM): the section lists the dev database's plans with their prices.
- 2026-11-04: **SUB-1 / FLEX-1 done, and the landing page reads as one page (owner: "blend together").**
  Subjects: new public `GET /api/v1/site/subjects` (`PublicSubjects`) - every active subject in the catalog, as many as
  there are: available (a published family curriculum - platform content only, teachers' own not public) with the
  ages its curricula cover, or in preparation; names and descriptions in the reader's language. The section shows
  them (painted place for Maths/Khmer/English, the places in turn for any other subject; the copy's short text where
  the catalog has no description), at most six with "And N more subjects in the app", "The subjects couldn't be
  loaded just now" on failure - no fixed three anywhere. Note: Khmer and English are not subjects in the catalog yet,
  so their cards appear (as "in preparation") once they are added in the Studio. Plans: at most three per audience.
  Blend: the sections share one paper ground - the light bands (for parents, safety, for children) fade in from it
  and back out instead of starting on a line, the dark bands (statements, schools) meet it along a soft river bank,
  the children's painting and the closing scene fade into and out of the paper. **Tested**: new
  `PublicSubjectsIntegrationTests` (available with ages 5-10 across two curricula; a subject without one in
  preparation; read by anyone), `AuthorizationSweepIntegrationTests` 2/2 (public allowlist +1, owner-approved); new
  `landingSubjects.test.tsx`; marketing + i18n Vitest 153/153. **Browser verified** (production build, 1280 px):
  every join between sections screened - no hard edge left.
- 2026-11-04: **LAND-1 FAQ built - questions and answers administrators keep, EN/KM (settles L-3 for the landing).**
  V86 `site_faq` (question and answer in both languages required, position, shown/hidden, version; six first
  questions answered only with what the product does - ages, cost via the plans above, safety, what a parent sees,
  languages, schools; Khmer drafts for PD-14). `SiteFaqService` like the statements: administrators only, each
  change audited, an edit over someone else's refused (409). Public `GET /api/v1/site/faq`; admin
  `GET/POST /api/v1/admin/site-faq`, `PUT/DELETE /api/v1/admin/site-faq/{id}`. Landing: "Questions families ask." -
  each question opens in place (native disclosure: keyboard and screen readers), in the reader's language, hidden
  when there are none. Operations -> Landing page: a Questions and answers panel (add, edit, order, show/hide,
  delete with confirmation). The sweep's public allowlist gained the endpoint; the protected `m2.test.tsx` admin test's
  mock answers the new read (owner-approved test updates). **Tested**: new `SiteFaqIntegrationTests` (seeded and
  public; create, hide, stale edit 409, missing Khmer 400, a parent refused, delete), sweep 2/2, `ServerText*`
  coverage 4/4; new `landingFaq.test.tsx` (order, opens in place; admin adds in both languages); marketing + admin +
  i18n Vitest 265/265. **Browser verified** (production build, V86 applied to the dev database): 1280 px EN and
  390 px KM - six questions, one opened, axe clean, no sideways scroll.
- 2026-11-05: **WORLD-1 slice 1 - each subject has its own place, not one scene for all.** `features/world/PlaceWorld`
  replaces the single river-bend painting on a subject's journey: the bridge (Maths) on the river bend with drifting
  clouds and water glints; the pavilion (Khmer) - the village seen close at the temple, morning gold, rising motes;
  the treehouse (English) - the village's forest edge, sunbeams and a swaying leaf canopy; the pond (other subjects)
  - mist, lily pads, a dragonfly. Depth: the painting and the foreground move apart with a fine pointer (not on touch,
  not with reduced motion); the scene comes into focus on arrival. Child space only. **Tested**: child + world Vitest
  65/65, tsc, eslint. **NOT yet browser verified** - next session: screenshot each place at 390/1280 px, check
  reduced motion and performance, tune crops (`ENVIRONMENTS` position/zoom), then WORLD-1 slice 2 (the village ->
  place travel transition) and EXP-1.
- 2026-11-05: **WORLD-1 slice 1 browser-checked and reworked.** Re-cropping two paintings could not make four places
  (the treehouse still read as the pavilion), and drawn leaf and lily-pad shapes looked cheap against the paper-cut
  art - removed. Each place now stands its own landmark (the owner's place illustrations: bridge, pavilion,
  treehouse, lotus pond) sharp and near in front of its painted surroundings, slightly out of focus as distance:
  the bridge on the river bend, the pavilion in the village in morning gold, the treehouse in the forest by the bend
  with shade and drifting leaves, the pond in mist with a dragonfly. **Browser verified** (dev gallery `/__play`,
  development builds only): all four at 1280 and 390 px; about 60 frames a second with the motion running.
  **Tested**: child + world + i18n Vitest.
- 2026-11-05: **WORLD-1 slice 2 - going into a place travels there.** Tapping a subject in the village (a marker on
  the painting, or a subject card on phones) runs a view transition: the village zooms toward that place's landmark
  and gives way, the place comes into view from a little nearer (`index.css` `::view-transition-*(world)`; both scene
  boxes share the name). `BrowserRouter` has no view-transition support of its own, so the link starts it
  (`useTravel`: `startViewTransition` + `flushSync(navigate)`); without browser support, with reduced motion, or a
  modified click, it is an ordinary link. In the place, Momo now stands between the continue card and the landmark
  instead of on it. **Tested**: child Vitest 63/63, tsc, eslint. **Browser verified** (production build, 1280 px, a new
  sample family): one view transition on entering Maths, origin at the bridge (43% 46%), arrival at the bridge scene;
  with reduced motion none ran and the link still went there.
- 2026-11-05: **EXP-1 slice 1 - starting an activity goes into it.** The player already moves with meaning (the
  introduction and completion pop in, questions turn like pages, a right answer bursts, the streak flame grows); the
  step into it was a plain page change. Play (on the village and in a place) now uses the same travel transition: the
  scene zooms in toward the path and gives way to the activity's introduction. Ordinary link without support or with
  reduced motion. **Tested**: child Vitest 63/63. **Browser verified** (production build): village -> bridge -> Play:
  two view transitions, the activity's "Let's go" introduction reached; with reduced motion none, same destination.
- 2026-11-05: **STREAK-1 slice 2 - parents see it too, worded as activity.** Each child on the family home reads
  "Learned N days in a row" (with the small flame) from two days up - the same server count the child sees, labelled
  as days with learning, not as understanding; nothing for a single day, or when it can't be read (a caregiver who
  only views progress is refused it). The completion screen's milestone moment already celebrates 3, 7, 14, 21, 30...
  **Tested**: new `homeStreak.test.tsx`; parent + i18n Vitest 223/223.
- 2026-11-05: **FLEX-1 checked across child, parent and landing - no fixed three subjects.** Every subject-specific
  map has a fallback for any other subject: `subjectTheme` (known motifs, then a colour chosen from the code),
  `places.ts` (the pond for every other subject), the landing's places (taken in turn) and short texts (the
  catalog's description first). The landing's subjects, the child's subjects and the parent's paths all come from
  the server's records. The hero's status line ("Early maths today...") is editable copy for the owner to keep true.
- 2026-11-05: **LAND-1 preview before publishing site words.** In Operations -> Landing page -> words, an entry being
  changed has "Preview on the page": the words as typed are kept in that administrator's own browser (localStorage,
  never sent anywhere; ignored after two hours) and the page they belong to (landing/trust `/`, try `/try`, sample
  parent view `/try/parent`) opens in a new tab with `?preview=copy`, showing them in place under a notice ("not
  published yet - only you see them") with "Leave the preview". Without the parameter, or for any other visitor, the
  page shows only the published words. Nothing about saving changed. **Tested**: new `copyPreview.test.tsx` (3);
  admin + marketing Vitest 123/123 (one earlier run had a single failure under load that three reruns didn't repeat).
  **Browser verified** (dev server): a stored pending headline shown on `/?preview=copy` with the notice.
- 2026-11-05: **EXP-1 - dialogs leave instead of vanishing.** New `usePresence` (components/motion/presence.ts): a
  closing surface stays for 150 ms while its exit plays, then goes; at once with reduced motion (or where motion
  can't be detected). The shared `Dialog` settles back and fades (`pop-out`) with its backdrop (`fade-in`/`fade-out`
  on `::backdrop`); clicks and Escape are ignored while it leaves; focus still returns to the opener after. The
  child's "Show me again" card does the same. Arriving and leaving classes are exclusive (both set `animation`).
  The dev-only `/__play` gallery has a dialog specimen for review. **Tested**: new `presence.test.tsx` (3); full
  Vitest 525/525 (`--maxWorkers=4`; with default workers this loaded machine times out unrelated studio tests at 5 s -
  they pass alone). `homeStreak.test.tsx` (mine, last commit) waits up to 4 s for the family's reads. **Browser
  verified** (dev server): dialog animation `pop-in` open, `pop-out` mid-close (opacity 0.84), removed after.
- 2026-11-05: **EXP-1 - handing over to the child is a change of place.** The five "Start <child>'s session" links
  (family home, child overview x2, caregivers x2) are `EnterChildLink`: the parent's page steps back and dims and
  the child's world comes forward from a little nearer (view transition on `root`, styled under
  `html[data-transition="enter-child"]`, attribute removed when it finishes). The child area's code is fetched first
  (at most 600 ms) so the world, not the loading screen, is what arrives. Ordinary link without support, with
  reduced motion or a modified click. **Tested**: parent + child Vitest 141/141. **Browser verified** (dev server,
  new local test family): one `enter-child` transition, mid-frame shows the world arriving over the parent page;
  with reduced motion none, same destination.
- 2026-11-05: **EXP-1 - and back to the grown-ups.** Completing the press-and-hold "Grown-ups" gate now lets the
  child's world give way (it lifts a little as it fades) while the grown-up page settles in (`leave-child`). Both
  directions share `components/motion/handOver.ts` (support/reduced-motion check, area preload, attribute cleanup);
  the village scene's own transition name moves with the page during a hand-over instead of its travel zoom.
  **Tested**: parent + child + components Vitest. **Browser verified** (dev server): `enter-child` then
  `leave-child`, back on the child's overview, attribute cleared; with reduced motion no transitions either way.
- 2026-11-05: **EXP-1 - coming back, the steps done since light up.** The chapter's step dots on the child's place
  remember (in memory, this tab only - `features/child/growth.ts`, nothing stored) how many were done when last
  shown; coming back with more done, those steps light up one after another and the next step pops in after them.
  First sight, reloads and unchanged progress don't animate; the count is always the server's. **Tested**: new
  `growth.test.tsx` (2); child Vitest 65/65 (+2). **Browser verified** (dev server, new local test family): place ->
  Play -> finish -> "My path": one `light-up` running on the step just done.
  Same for the bud row under the scene ("N of M lit"): the buds opened since open in turn (browser: 2 `light-up`).
- 2026-11-05: **Simplification - one way to transition a navigation.** The village's travel (`useTravel`) and the
  parent <-> child hand-over now share `components/motion/handOver.ts` (support and reduced-motion check, optional
  preload, optional page-wide kind) and `plainClick` (new-tab clicks stay links); the duplicated checks in
  ChildHome and EnterChildLink are gone. Menus deliberately keep closing at once (choosing an item should feel
  immediate, and an in-flow menu lingering would shift the anchor it scrolls to). **Tested**: child + parent Vitest
  143/143. **Browser verified** (dev server): enter-child -> travel -> Play (3 transitions), back to the grown-ups
  (leave-child); none with reduced motion. **Sweep**: 15 family and public pages at 1280 and 390 px on a new test
  family - no page errors, console errors/warnings, failed API calls or horizontal overflow.
- 2026-11-05: **EXP-1 - the child's tabs: the pill slides to the tab chosen.** On a child's page (Overview / Skills
  & progress / School & settings) the dark pill moves from the tab left to the one chosen (300 ms, the product's
  out curve), while the tab's content arrives as before (Arrival). Placed without moving on first sight; re-placed
  when the tabs resize (language, width); with reduced motion it just moves. **Tested**: parent Vitest 78/78.
  **Browser verified** (dev server, 1280 and 390 px): pill mid-way between the tabs 120 ms after the click, then
  exactly under "Skills & progress".
- 2026-11-05: **EXP-1 - parents' progress bars fill as they arrive.** The family home's per-subject bar and the
  chapter bars fill from the left (`fill-bar`, 700 ms, scale only - no layout work) to the server's share; the
  numbers beside them are unchanged and read first. Reduced motion: drawn full at once. **Tested**: parent Vitest.
  **Browser verified** (dev server, 1280 and 390 px): the family home's bar runs `fill-bar` on arrival.
- 2026-11-05: **WORLD-1 - what further places need: the owner's artwork.** Rice fields and gardens (owner direction)
  need landmark paintings in the Figma style of the existing four (bridge, pavilion, treehouse, pond); drawn
  stand-ins looked cheap and were removed earlier. Until then every subject beyond Maths, Khmer and English stands at
  the lotus pond. Adding a place is one entry in `features/world/places.ts` plus its scene in `PlaceWorld.tsx`.
  **Checkpoint**: backend unit 209/209, Vitest 525/525, tsc, eslint, production build - all local.
- 2026-11-05: **LAND-1 inventory done - every landing text is editable, except the product's own words.** Editable
  EN/KM in Operations -> Landing page: all of `features/marketing` and `trust` (nav, hero, CTAs, every section
  heading and description, plans framing, footer), the questions (FAQ, `site_faq`), what families said
  (statements/testimonials, M2, with who said it and permission), plans and subjects (from their records), with
  preview before publishing. Deliberately **not** landing copy, because they are the product's own words shown as
  the product shows them: the buddies' names and lines (`buddy`), the sample's standing and evidence labels
  (`learning`: "Progressing", "Enough evidence"...), and the menu buttons' labels (`common`). Changing them on the
  landing alone would show families words the product doesn't use. The page carries no statistics. Layout with
  longer and shorter words: checked for the hero (LAND-1 slice 1) and the FAQ in Khmer at 390 px.
- 2026-11-05: **LAND-1 - the hero holds with a much longer headline.** Checked with the new preview (a 120-character
  headline and a lead twice as long, at 390, 1280x720 and 1440x900): no overflow or overlap, but the headline grew
  into a block that pushed the actions out of the first screen. A headline over 72 characters (the original is 57)
  now steps down a size; the actions stay in the first screen at all three sizes (bottom 614/665/711 px).
  **Browser verified** (dev server, preview mode).
- 2026-11-05: **LAND-1 defect fixed - the Khmer hero pushed its actions below the fold at 1024x768.** Cause: the
  Khmer headline used the English size, and Khmer letters stand taller and stack below the line - four tall lines
  (403 px). It now has its own size and line height (1.32, so stacked consonants don't touch the line below).
  Measured in Khmer, first action's bottom: 390x844 476, 768x1024 484, 1024x768 611 (was 795), 1280x720 498,
  1440x900 593 - all in the first screen, no horizontal overflow. English unchanged. **Browser verified** (dev).
- 2026-11-05: **Defect fixed - on a phone, the chosen child tab could sit off-screen.** At 360 px the three tabs
  don't fit and scroll sideways (by design); opening "School & settings" left it past the edge. The tabs now
  scroll sideways to keep the chosen one in view (the page doesn't move). **Sweeps (dev server)**: parent pages in
  Khmer at 360 px - no errors, failed calls or horizontal overflow; the child's space in Khmer at 360 and 1280 px
  (village, place, shelf, garden) - no overflow, clipping or cramped Khmer lines; public pages in Khmer at 390 and
  1280 px - no cramped Khmer lines.
- 2026-11-05: **EXP-1 - the buddy answers a tap.** On the village and in each place the buddy is a button ("Say
  hello to Momo"): a tap makes it cheer (the jump replays on each tap) and say its own one-line character - the
  authored line the child read when choosing it (`buddy.characters`, EN/KM), never generated - for 4.5 s, then its
  usual words come back. The bubble is a polite live region, so the line is read out too. Learning Buddy stays an
  authored character, not a conversation (CLAUDE.md §19). **Tested**: new `buddyHello.test.tsx`; child Vitest 66/66.
  **Browser verified** (dev server, new local test family): tap -> the otter's line and `cheer` running; with reduced
  motion the line, no jump.
- 2026-11-05: **Defect fixed - on phones the child's top bar broke onto two rows.** At 360-390 px the name, buddy
  and flame chips plus language, sound and grown-ups buttons didn't fit (a 6-letter name wrapped at 360, a 9-letter
  one at 390). Below 400 px the name chip drops its initial (it repeats the name beside it, hidden from screen
  readers), gaps tighten a step, and in Khmer the language button says "EN" (spoken: "Switch to English"); touch
  targets stay 44 px. **Browser verified** (dev server): one row for Dara/Sophea at 360, Chanthavy at 390, Dara at
  640; no overflow. **Tested**: child + i18n Vitest.
  Same pass: the chapter's eight step dots wrapped at 360 px ("8" alone on a second row); below 400 px they are
  28 px with a 4 px gap - one row at 360/390/640 (browser verified).
- 2026-11-05: **Defect fixed - finishing an activity made a phone's page scroll sideways.** The completion
  celebration's motes reach up to 212 px from the centre, past the edge of a 360 px screen (page 399 px wide, so it
  could be dragged sideways). Their sideways reach is now kept within the screen (`clamp(-44vw, x, 44vw)`) instead
  of clipping the page. **Browser verified** (dev server, a whole activity at 360 px): completion page 360/360 px;
  the question screen settles at 0 px overflow.
- 2026-11-05: **Sign-in pages at 360 px, EN and KM: clean; one small target enlarged.** login, register, forgot,
  reset and verify (expired-link state): no overflow or console errors in either language. The forgot page's
  inline "use your phone / your email instead" buttons were 20 px tall; their hit area is now 44 px (padding with a
  matching negative margin - the text doesn't move). The 32 px language buttons meet WCAG 2.5.8 (24 px) and stay.
  Also: the public try lesson played to completion at 360 px - no overflow or errors.
- 2026-11-05: **WORLD-1 - the way back is travel too.** "All my subjects" from a place now steps the place back and
  pulls the village out from that place's landmark (the reverse of going in: `travel-back`, the landmark's spot in
  the village computed as the village places it). Ordinary link without support, with reduced motion or a
  new-tab click. **Tested**: child Vitest. **Browser verified** (dev server): village -> bridge -> All my subjects:
  transitions enter-child, travel, travel-back (origin 43% 46%, the bridge), back on the village; none with
  reduced motion.
- 2026-11-05: **For the Khmer review (PD-14) - new Khmer drafted this window** (Claude-drafted, like all Khmer so
  far): `features/marketing/copyPreview` (notice, leave), `features/admin/operations/siteCopy` (preview,
  previewHint), `features/child/entrance` (sayHello), plus the earlier `streak`, FAQ seed (V86) and landing
  `noLimit/pricesFailed/ages/failed/more/faq` keys. Native review before real families, as for the rest.
- 2026-11-05: **STREAK-1 - the home/shelf milestone moment: held for the owner.** The completion screen already
  celebrates a milestone (3, 7, 14...) the moment it is reached; repeating it on the home seconds later adds
  nothing, and a "best streak" on the shelf would show a child what they lost when the count restarts - against
  the approved "no guilt" rule. Not built; if wanted, say which (and whether "best" may ever be shown).
- 2026-11-05: **Accessibility check of this window's screens (axe, WCAG 2.0-2.2 A/AA): 0 violations** at 390 and
  1280 px - landing in preview mode (with its notice), child overview and Skills tabs (sliding pill), family home
  (streak line, filling bars), village after a buddy tap (live line), a place. The admin editor's Preview button was
  not scanned (staff sign-in needs an authenticator code); its Vitest passes.
- 2026-11-05: **WORLD-1 / EXP-1 - the child's own corners are places too.** "My shelf" (village and all-done card)
  and the buddy chip step in (the page steps back, the corner comes forward); "Back to my path" on the shelf and
  the buddy page steps out (`StepLink`, `step-in`/`step-out` in index.css, the village scene moving with its page).
  One view transition per move, no extra page fade. **Tested**: child Vitest 66/66. **Browser verified** (dev
  server): enter-child, step-in (shelf), step-out, step-in (buddy), step-out - attribute cleared, back on the
  village; none with reduced motion.
- 2026-11-05: **Simplification - one transition link.** `StepLink` and `EnterChildLink`'s own handler merged into
  `components/motion/TransitionLink` (kind + optional preload); `EnterChildLink` is now a two-line wrapper
  (enter-child + child-area preload). Same behaviour (browser re-run identical), child + parent Vitest 144/144.
- 2026-11-05: **Defect found by CI and fixed - the dialog exit kept the page inert.** CI on `4c6bea8`: backend,
  frontend and deployment green; e2e failed on the phone run of `plan-journey` ("Find family" never enabled). Cause:
  my dialog exit (2642d03) kept the closing `<dialog>` modal for 150 ms, so the page behind stayed inert and the
  email typed right after the password dialog closed went into the dialog - the search box stayed empty. A closing
  modal must release the page at once: the shared Dialog closes immediately again (its backdrop still fades in on
  opening). The child's "Show me again" card keeps its exit - it is not a modal `<dialog>` and focus returns to its
  button at once. `usePresence` stays for non-modal surfaces only. Also merged: village travel, Play and the way
  back use `TransitionLink` (origin option), `useTravel` removed. **Tested**: components/admin/parent Vitest 200/200,
  child + parent 144/144; travel re-checks identical. **Browser verified**: dialog gone 20 ms after closing.
- 2026-11-05: **Phone performance of the world, measured.** 390 px, CPU throttled 4x (headless Chromium): the
  village idle, a place arriving (blurred painting, foreground, landmark rise) and the place idle all run at
  16.7 ms per frame (p95 16.8 ms), no frame over 50 ms. No change needed; real low-end devices remain to be tried
  (OD: device lab).
- 2026-11-05: **The hand-over rarely waits.** A `TransitionLink` with a preload (the parent's "Start <child>'s
  session") starts downloading the child area as soon as it is pointed at or focused, so the up-to-600 ms wait
  before the transition is usually already over by the click. **Tested**: parent + components Vitest 92/92.
  **Browser verified**: enter-child / leave-child unchanged, none with reduced motion.
- 2026-11-05: **CHECKPOINT (end of window 3) - next window starts here.** Pushed through `ab1d9ad` (CI for the
  last pushes not checked by the owner's instruction; `962bfd6` green; `4c6bea8` e2e red - fixed in `dd93c92`,
  check CI on the head first). Done this window: WORLD-1 places + travel in/out + corners step in/out; EXP-1 Play
  into the activity, hand-over both ways (preloaded), steps/buds lit since, tab pill, bars fill, buddy answers a
  tap; LAND-1 preview, inventory, long-headline and Khmer hero fixes; STREAK-1 parent line; defects at 360 px
  (top bar, dots, celebration overflow, chosen tab, auth targets). Held for the owner: streak milestone on
  home/shelf (and whether "best" may show); landmark art for more places. Next: check CI; then Priority 3/4 -
  staff workspaces at 360 px (needs an authenticator code), further simplification of ChildHome.tsx (large).
- 2026-11-05 (window 4): **STREAK-1 - the current streak on the child's home and shelf (owner, 2026-11-05).**
  `StreakCard`: the animated flame with the server's current streak - "N days in a row" glowing, then "You learned
  today - your flame is glowing." or the invitation "Learn today and your flame grows."; no streak: an unlit ember,
  "Light your flame - finish an activity and it lights up." No best-ever count, no loss words, and the milestone
  celebration stays on the completion screen only. On the village beside the greeting and on the shelf under its
  heading. **Tested**: new `streakCard.test.tsx` (3: none / lit not today / today; no "best", "lose", "miss");
  child + i18n Vitest 218/218. **Browser verified** (dev server, new local test family, 1280 and 360 px): "Light
  your flame" before, "1 day of learning ... glowing" after finishing an activity, on the village and the shelf;
  no overflow.
- 2026-11-05 (window 4): **WORLD-1 - six distinct places, from the owner's own artwork.** The unused Figma exports
  (`D:/bk-work/figma-assets`) held a sapling with its roots, a pavilion on its own island and a second painting -
  the open river with a jetty. Now: Maths at the bridge, Khmer at the pavilion, English in the treehouse, **science in
  the garden** (the sapling, on the river path's green bank, fresh morning light, drifting seeds and two
  butterflies), **the arts on the island** (the island pavilion in the open-river painting, warm evening light,
  glints), every other subject at the lotus pond. New landmarks stand on the village painting (garden on the left
  bank, island in the open water) and the dev gallery `/__play` shows all six (`PLACE_KEYS`). **Activities too**:
  an activity's cover now opens onto its subject's own place (its painting, light and landmark, the buddy beside
  the landmark) instead of one river picture for every subject; practice (no path) keeps the river. Art converted to
  WebP in the browser (sapling trimmed of its empty margins): `science-garden.webp` 56 KB, `arts-island.webp` 37 KB,
  `open-river.webp` 27 KB. **Tested**: new `places.test.tsx` (mapping, six distinct landmarks, each scene renders);
  child/world/brand Vitest. **Browser verified** (dev server): garden and island scenes at 1280 and 390 px; a Maths
  activity's cover opens onto the bridge at 1280 and 360 px, no overflow. Science/arts subjects are not in the dev
  catalog, so their places were checked in the gallery, not through a real path.
  **Asset requirements still open (owner/illustrator)**, Figma style of the existing set:
  1. *Open river* panorama at 1584 x 672 (plus 900 x 382) - only a 512 x 217 export exists; it is soft on large
     screens (blurred as distance, acceptable for now).
  2. *Garden* backdrop painting 1584 x 672 (+ 900 x 382) - the garden borrows the river path's bank today.
  3. *Rice fields* place (owner's list): a landmark (transparent PNG, ~640 px wide, like the bridge) and its
     backdrop (1584 x 672 + 900 x 382), then one entry in `places.ts` and its scene in `PlaceWorld.tsx`.
- 2026-11-06: **WORLD-1 on the landing - each subject shows its own place.** The landing's subject cards gave any
  subject beyond Maths, Khmer and English one of those three landmarks in turn (a science card could show the Maths
  bridge). They now use the same mapping as the child's world (`subjectTheme` -> `placeFor`): science the garden,
  the arts the island, others the pond. **Tested**: marketing Vitest 15/15, tsc.
- 2026-11-06: **DEPLOYED for a short pilot (owner: about 10 days).** https://bookeyond-kid.vercel.app (Vercel) -> /api
  -> https://backend-production-0be94.up.railway.app (Railway, PostgreSQL, /data volume, trial). The record, what was
  verified and what the owner still checks: `docs/DEPLOY-VERCEL-RAILWAY.md`, "Deployment record 2026-11-06". Fixed on
  the way: Dockerfile cache mounts (Railway), fonts inlined as data: URLs refused by the CSP (seen live). Owner next:
  sign in as administrator, choose demo lessons, register a parent with a real address (Brevo delivery), add a child,
  play. Development continues from the roadmap checkpoint (the window-4 items above).
- 2026-11-06: **Live fixes after deployment.** Email over Brevo's HTTPS API (Railway's trial blocks SMTP), the owner's
  admin sign-in verified, and per-client rate limits made effective on Railway (trusted edge + the browser calling the
  API directly; Vercel proxies media only). Details: `docs/DEPLOY-VERCEL-RAILWAY.md`. Live = `405718e`. Development
  resumes from the window-4 checkpoint; nothing is redeployed without the owner's say-so.
