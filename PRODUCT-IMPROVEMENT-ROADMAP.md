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
| M4 Self-service school registration and paid onboarding | next | | |
| M5 Authoring UX, direct upload, content visibility | planned | | |
| M6 Feature-specific paywall messages | planned | | |
| M7 Pagination and information density | planned | | |
| M8 Payment QR, manual reconciliation, secure grants | planned | | |
| M9 Phone registration and verification foundations | planned | | |
| M10 Child access across devices, trusted caregivers | planned | | |
| M11 Storage-provider abstraction, school storage choice | planned | | |
| M12 School management: foundation and first modules | planned | | |
| M13 Integration, role verification, docs, staging readiness | planned | | |

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
| D-12 | New third-party libraries (e.g. libphonenumber, an S3 client) and container images (S3-compatible test server) | Asked at the milestone that needs them |

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
  - **Next**: M4.
