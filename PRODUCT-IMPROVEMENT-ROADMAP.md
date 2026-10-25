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
| M12 School management: foundation and first modules | planned | | |
| M13 Integration, role verification, docs, staging readiness | planned | | |

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
| D-09 | Content for ages 3 and 10–12 | Copy says 3–12; subjects follow the age hubs the admins configure |
| D-10 | Genuine testimonials (with permission) | Only brand statements |
| D-11 | PDP law status, child-safety page, consent for school student records (PD-31, PD-32) | Minimal data; school records stay school-scoped |
| D-12 | New third-party libraries (e.g. libphonenumber, an S3 client) and container images (S3-compatible test server) | Asked at the milestone that needs them. **libphonenumber approved 2026-10-24** (M9b; pinned 9.0.38, two months old when added) |
| D-13 | How a co-teacher uses a lesson shared with their school (decided 2026-10-23) | **Copy to own**: the colleague copies it into their own content (drafts, crediting the original); children's paths still draw only on their curriculum owner's and platform content |
| D-14 | PUBLIC content for every teacher (decided 2026-10-23) | **Deferred** until it is decided who reviews teachers' content before the whole platform sees it |
| D-15 | Phone-only accounts; resetting a password by text (decided 2026-10-25) | **Email stays required** on every account; a code texted to the verified phone can also reset a forgotten password |

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

