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
| M12 School management: foundation and first modules | planned - **needs the owner first**: linking a school's student record to a family's child (consent, Battle 4C "school <-> child authorization" is not designed) and whether school administration (attendance, exams, report cards) is in scope now - CLAUDE.md keeps children and parents first and warns against premature school-ERP | | |
| M13 Integration, role verification, docs, staging readiness | planned | | |
| L1 Learning experience: "Learn first" activity introductions (owner brief 2026-10-25) | **done** | see log | V73 |
| L2-L11 Learning experience follow-ups (explanation tip, D-16 hints, recorder, D-17 two days, phone layouts, Studio gaps/guide, parent fixes and help) | **done** | see log | V74 |
| M13 Integration, role journeys | role journeys on phone (360 px) EN/KM for all five roles **done** 2026-10-27; readiness docs remain | | |
| F1a Landing page: every word editable without code | **done** 2026-10-27 | see log | V75 |
| F1b Public demo journey for families: a child tries a real activity (buddy, introduction, questions, feedback, completion) then "Create a parent account" or "Not now"; what an account enables | **done** 2026-10-27 (teach-first, account explanation, Not now, demo picker shows replaced versions) | see log | - |
| F1c Parent demo with representative sample data (progress, evidence, how to help) | **done** 2026-10-27: `/try/parent`, a fictional sample child labelled on the page, static, nothing fetched or stored | see log | V76 |
| F1d Landing page redesign: concise copy, purposeful Learning Buddy/character motion showing the real app | **F1d-1 done** 2026-10-27: "How it works" plays the learning loop (child answer -> evidence -> standing -> next step) with the real components; **F1d-2**: closing headline and footer line editable, parent demo linked from "For parents"; **F1d-3**: "For parents" says what the product actually does; **F1d-4**: the buddies can be chosen on the page, as a child does; **F1d-6**: shorter on phones (redundant "For families / For schools" removed) | see log | |
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

