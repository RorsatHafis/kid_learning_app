# Bookeyond Kid — Product journey review and gap analysis (2026-11-01)

A product-wide review requested by the owner: where Bookeyond Kid stands as **one connected product**, what is
unfinished or overlooked, and what to build next. It supplements - does not replace - `docs/BUSINESS-REQUIREMENTS.md`
(what the product is), `PRODUCT-IMPROVEMENT-ROADMAP.md` (the working record and its decisions table),
`docs/SCHOOL-MANAGEMENT.md`, `docs/SUBSCRIPTIONS.md` and `LOCALIZATION.md`.

## How this review was done - and its limits

- **Verified in this review (2026-11-01)**: the frontend route map of every area (`App.tsx`, `*Area.tsx`), every
  backend controller and its endpoint count, the migration count (V1-V85), the email senders, the decision tables
  (roadmap D-*, `SCHOOL-MANAGEMENT.md` S1-S6 and B1-B5, `SUBSCRIPTIONS.md` SB1-SB9 and L-2, `LOCALIZATION.md` L-1),
  the business requirements' feature inventory, and CI (`1303b4a`: all jobs green, including the end-to-end journeys
  on the Docker stack).
- **Evidence from earlier sessions, not re-traced today**: browser walks of every role at 360 and 768 px
  (2026-10-27/30: navigation pages clean - no overflow, no serious axe findings, no API errors), the child's
  bilingual session (2026-10-30), Studio authoring flows, plan and payment flows (integration tests). These are cited
  as such; a workflow below marked *traced* was exercised end to end by a test or a browser run at least once.
- **Not traced at all**: real email delivery to real inboxes, a real SMS, a real card or bank payment, a production
  deployment, backup restore on production data, native-speaker Khmer quality. They depend on accounts the owner has
  not yet created (see "External dependencies").
- Usage limits shaped the depth: this review reads code structure and test evidence; it does not re-read every
  service. Where a statement is an assumption, it says so.

## 1. Overall maturity, in one paragraph

The **learning platform for families** is the most mature part: a parent can register, add a child, see curricula by
age, and the child plays real activities with authoritative server-side scoring, evidence, mastery, Smart Review,
explanations and hints, in English or Khmer, on a phone; the parent sees evidence-grounded progress. **Content
authoring** is substantial (questions of seven types, activities with introductions, bilingual inputs, preview per
language, validation, versioning, reuse, copy, sharing) - but **there is almost no real content**: every installation
starts from one English developer seed (D-19). **Schools** can use the platform for learning (classes, teachers,
family-granted access, assigned practice, multi-branch organizations, academic years) and pay Bookeyond, but **school
administration** (student records, attendance, assessments, report cards, fees) does not exist and waits on owner
decisions S1-S3. The **subscription business** is configurable (plans, limits, versions, offer dates, manual
payments, invoices) but has no automatic renewal, trials or online card payments (external credentials and SB
decisions). Nothing is deployed for real users yet. Assessment: **a credible, well-tested pre-launch product for
families; not yet a launchable product** - the blockers are content, launch operations (deployment, email, legal,
Khmer review) and the owner's decisions, more than code.

**Adaptive engine, traced from code** (inputs, mastery formula, decision table, worked examples, findings AE-1..AE-5):
see [`ADAPTIVE-ENGINE.md`](ADAPTIVE-ENGINE.md) (2026-11-03, INSPECTED).

**Identity and permissions, and one person with several roles (R-1)** (permission matrix, today's behaviour per
real situation, options A/B/C with a recommendation, decisions R-1a..R-1d, findings ID-1..ID-3): see
[`IDENTITY-PERMISSIONS.md`](IDENTITY-PERMISSIONS.md) (2026-11-03, INSPECTED).

**Security, privacy and reliability** (threat model by asset and threat with each control's evidence, failure modes,
measured performance, open items S-ENUM-1, TD-15, SR-*): see [`SECURITY-AND-RELIABILITY.md`](SECURITY-AND-RELIABILITY.md)
(2026-11-03, TESTED/RUNTIME where stated).

## 2. Coverage matrix by journey

Status key: **V** implemented and verified (tests and/or browser) · **PV** implemented, partially verified · **P**
partially implemented · **UI/BE** only one side exists · **PL** planned, not implemented · **BD** blocked by a decision ·
**EX** depends on an external service or configuration · **NC** not yet considered, potentially necessary.

### A. Public discovery

| Workflow | Status | What works | Missing / can fail | Priority |
|---|---|---|---|---|
| Landing page, EN/KM, editable copy | V | F1a-F1d; every word admin-editable in both languages | Testimonials: none exist (honest, but less persuasive) | - |
| Try a lesson / parent demo | V | Real lessons, nothing stored; sample child labelled | Demo shows only what the admin picked (0-3 activities) - with one seed curriculum, little to show (D-19) | High (content) |
| Pricing on the landing page | P | Plans section is editable site copy | It is **not linked to the plan records**: a price changed in Admin -> Plans must also be changed in the copy by hand - risk of two different prices | High |
| School offering explained | P | Landing copy | Nothing explains what schools get vs families in detail; no "contact sales" form (schools self-register) | Medium |
| Trust, privacy, legal before sign-up | EX | Legal agreement at registration is built (Y15) | Texts not published (`LEGAL_*`), drafts only (F2b) - **launch blocker** | Essential |

### B. Registration, authentication, onboarding

| Workflow | Status | Notes |
|---|---|---|
| Parent and principal registration, email confirmation, sign-in, refresh rotation, reset, email change | V | Real tokens; rate-limited. Real delivery needs the production mail provider (EX). |
| Phone sign-in / reset | EX | Built; only a local test sender (D-04). |
| Teacher invitation and acceptance | V | `SchoolStaff`. |
| Caregiver invitation | V | M10b. |
| Platform admin with authenticator and step-up | V | M9a/M8a. Second and further administrators: Admin -> Staff (step-up, audited); the first by the startup bootstrap. *(The earlier "no in-product way" note was wrong - corrected 2026-11-03.)* |
| Organization owner / branch principals | V | Owner appoints branch principals (M12 B1). |
| Onboarding guidance after sign-up | P | Add-child flow guides parents; principals get a setup path. Teachers get five first steps on their home (2026-11-03); principals see what is still to set up per class (teacher, curriculum, join code) on the school overview. |
| Duplicate / ambiguous accounts | P | One account per email; a parent who is also a teacher uses one account with one role - **a person with two roles needs two emails** (current role model, `PlatformRole` single value). Real for Cambodian schools where teachers are parents (NC, high for schools). |

### C. Parent and family

| Workflow | Status | Notes |
|---|---|---|
| Add / manage children, language per child, buddy | V | |
| Plans: see plan, ask for another, limits | V | Self-service families: request -> admin confirms (no online purchase - PD-09/EX). |
| Progress, standings with evidence strength, insights, "why this next", helping at home | V | Grounded in evidence (2C, 5C). |
| Daily limits, devices, caregivers, school access grants | V | |
| Export, deletion with grace period | V | |
| **Notifications** (progress digest, "your child finished", a review is due) | NC | Only transactional emails exist (auth, invitations, plan requests). CLAUDE.md forbids engagement-maximizing notifications; a **calm weekly summary** is a different thing - needs an owner decision (proposed **N-1**). |
| School information for a child also enrolled at a school | P | Parent grants/withdraws access; **parents see nothing from the school** (no announcements, attendance, marks - those modules don't exist, S2). |

### D. Children's learning

| Workflow | Status | Notes |
|---|---|---|
| Home, path of books, subjects, chapters | V | Child's language enforced first (`ChildLanguageGate`, 2026-10-30). |
| Instructions, "learn first" introduction, questions one per screen, 7 types | V | |
| Answer submission, server scoring, feedback, correct answer shown, explanation or hint after a mistake | V | |
| Retry after a wrong answer | BD | D-20: one try today. |
| Completion, progression, Smart Review, mastery with evidence over days | V / BD | D-17 built; D-18 (bring a skill back to confirm it) open. |
| Garden, shelf, replay as practice | V | No points/streaks/leaderboards. **Finding**: a streak service and `GET /children/{id}/streak` exist in the backend but no screen uses them - dead code against CLAUDE.md's no-streak rule; remove or record as intentionally dormant (low). |
| Empty states (no curriculum fits, all done, offline) | PV | Tested in earlier milestones; "all done" depends on content volume - with one seed curriculum a child reaches the end quickly (D-19). |
| Age fit 3-12 | P | Audience confirmed 3-12; **content for 3-5 (pre-readers) and 10-12 doesn't exist** - recordings help pre-readers but need an educator to record them (D-19). |
| EN/KM consistency through a session | V | Questions in Khmer only when complete (L-1); names, activities, plans; screen-reader language from metadata (V83/V84). Khmer is Claude-drafted (PD-14 native review pending - EX). |

**Engagement vs learning**: the design deliberately avoids engagement mechanics; evidence and mastery rules are the
strongest part of the product. The weak point is not the engine but **the amount and age-range of content** it runs on.

### E. Teacher and content authoring

| Workflow | Status | Notes |
|---|---|---|
| Classes, students (family-granted), skills, trends, assigned practice | V | |
| Catalog (subjects, skills, chapters, curricula) - admin governs, teachers use | V | |
| Questions (7 types), hints, explanations, pictures, recordings, bilingual | V | |
| Activities, introductions, builder, reuse, copy, versions, publish, withdrawal | V | |
| Preview as a child per language (question and activity) | V | |
| Validation in plain words; translation status in bank and picker | V | |
| Own content isolated per teacher; school sharing by copy | V | |
| Content review / approval before children see teacher content | P | `ContentReview` exists (Y14) for platform review; **teacher content reaches only their own classes** - is a principal's review needed? (proposed **C-1**, low) |
| Unsaved work | V | Guards for question, introduction, More settings, plan drafts, and closing a Manage dialog (names) asks first (2026-11-03). |
| Bulk import (questions from a spreadsheet) | V | 2026-11-03: paste rows (or start from the downloadable template, EN+KM columns) into the bank or straight into an activity being written; every row a draft, all or none, with its publishing checks; five types (pictures, matching, sorting stay in the form). Not built: CSV/XLSX file upload, bulk publish (by design: activities publish with their questions). |
| A teacher's first-run guide | V | 2026-11-03: five derived first steps on the teacher's home (school, class, curriculum, children, own content). |

### F. School and multi-branch

| Workflow | Status | Notes |
|---|---|---|
| School registration, activation by payment, branches, branch principals, organization overview | V | B0-B2. |
| Academic years, terms, calendar copy, year-end class rollover | V | M12 P1. **Doc discrepancy fixed**: `SCHOOL-MANAGEMENT.md` section 1 still said "Missing". |
| Staff permissions beyond teacher/principal (registrar, cashier) | BD | S4. |
| School student records, admissions, guardians, transfers | BD | S1 (record vs family child) - **the root decision for all school administration**. |
| Attendance, assessments, report cards | BD | S2. |
| School fees, invoices to families, receipts, ledger | BD | S1 + S3; online collection S5/EX. |
| Parent-school communication (announcements) | PL | No module; when built it must be bilingual (LOCALIZATION.md). |
| Consolidated group reporting | P | Counts and payment state per branch only. |
| A school joining an organization later | BD | B5. |

Boundaries verified by tests: branch principals can't see other branches' data; organization owner sees aggregates;
school access to a child needs the family's grant; school access never exposes family-private data.

### G. Subscriptions and money

| Workflow | Status | Notes |
|---|---|---|
| Plans as configuration (limits, prices, versions, offer dates, EN/KM words), entitlements enforced on the server | V | New offers need no code unless they introduce a **new kind of limit** (a lever is code by design - `SUBSCRIPTIONS.md` 3). |
| Families/schools subscribe; admin confirms manual payments with step-up; invoices, payment history | V | |
| Online payment (ABA PayWay) | EX | Built, disabled; needs merchant credentials. KHQR image needs owner config (D-07). |
| Renewal, expiry, grace, failed payment | P/BD | Periods end; no automatic renewal (SB2, SB3, EX). **Risk**: with manual payments only, an admin must renew every customer by hand - fine for a pilot, not at scale. |
| Trials, introductory prices, promotions | BD | SB5, SB6. |
| Organization billing | BD | SB1/B1 (per branch today). |
| School fees vs subscription billing kept apart | V (by absence) | Only Bookeyond billing exists; the separation is designed (`SUBSCRIPTIONS.md` 1). |

### H. Platform admin

| Workflow | Status | Notes |
|---|---|---|
| Accounts, suspension, staff, schools, plans, payments, landing copy, content studio, media, storage, reports, audit log, system page | V | |
| Support investigation (see a family's state without acting as them) | P | Admin sees accounts, families' plans and schools; **no read-only "what does this child see" view** for support (NC, medium). |
| Monitoring, alerting, error tracking in production | NC/EX | Health endpoint exists; no production monitoring configured (deployment not done). |
| Backups and restore | P | Restore procedure fixed earlier (TD-26); **not rehearsed on a production database** (EX). |
| Operational reports | P | Subscriptions summary, admin insights; no revenue report beyond payments list (later). |

## 3. Connections between modules

| Chain | State | Weak link |
|---|---|---|
| Registration -> onboarding -> plan -> access | V | Default plan gives access; limits enforced. |
| Parent -> child -> learning -> progress -> insights | V | Content volume (D-19). |
| Curriculum -> skills -> content -> activity -> answer -> feedback -> mastery -> review | V | D-18, D-20 are policy, not breaks. |
| Teacher -> class -> students -> assigned practice -> progress | V | Children must be brought in by families (by design, S1). |
| School -> branch -> year -> class -> enrollment -> attendance -> results | P | Stops at class: attendance/results don't exist (S2). Class enrolment isn't tied to an academic year's student record (S1). |
| Organization -> branches -> admins -> permissions -> reporting | P | Reporting is counts only. |
| School fees -> invoices -> receipts -> reports | PL/BD | Not built. |
| Plans -> payment -> entitlements -> renewal/expiry | P | Renewal manual. |
| EN/KM authoring -> storage -> preview -> publish -> child language | V | Native review pending (PD-14). |
| Landing pricing -> plan records | **Broken by design** | Two sources of truth (see A). |

## 4. Missing capabilities worth considering (problem, who, phase)

| Capability | Problem it solves | Who | Phase |
|---|---|---|---|
| **Real curriculum content and its production workflow** (D-19) | Without it nothing else matters to a family | Owner, educators | **Essential** |
| Launch operations: production deployment, mail provider, monitoring, backups rehearsed, legal texts, Khmer native review | Can't serve real families safely without them | Owner + engineering | **Essential** |
| Plan prices shown from plan records on the landing page | Prevents wrong prices | Families, schools | Essential (small) |
| Weekly calm progress summary for parents (N-1) | Parents don't visit dashboards daily | Parents | Next release (decision) |
| One person, two roles (parent + teacher) | Common in Cambodian schools | Teachers who are parents | Next release (design decision R-1) |
| Teacher first-run guide; school setup checklist | "What do I do now?" after sign-up | Teachers, principals | **Built** 2026-11-03 (teacher first steps; principal per-class to-do) |
| Support read-only view of a child's state | Answering parents' questions | Platform admin | Next release |
| Question import from a spreadsheet | Bringing existing worksheets | Teachers, content team | **Built** 2026-11-03 (paste + template; file upload later) |
| School administration modules (records, attendance, assessment, fees, announcements) | Schools running operations | Schools | Later, after S1-S3 |
| Low-connectivity tolerance (resume after drops is built; offline play is not) | Rural connections | Children | Later (measure first) |

Not recommended now: leaderboards, coins, streaks (product rule), a chat-based buddy, PUBLIC content sharing (D-14),
timetables and rooms.

## 5. Open decisions

| Decision | Affects | Options | Recommendation | Blocks | Doesn't block |
|---|---|---|---|---|---|
| D-18 | Whether a skill waiting for "another day" is brought back | (a) nothing; (b) light review item next learning day | (b) - completes D-17's intent | That review item | Everything else in learning |
| D-19 | Real curriculum: who writes it, which languages, review | owner's team / partner educators / teachers | Owner's content team writes a first EN+KM curriculum per age band with educator review | Launch | All tooling |
| D-20 | Second try before the answer is shown | one try (today) / second try then answer | Second try for choice questions only, first try's evidence kept as the evidence | Retry UI and evidence rule | Feedback, hints |
| L-1 | Incomplete Khmer question shown in English | whole question English (today) / hide / mix | Keep (honest) | - | - |
| L-2 | Offered plan's Khmer | decided 2026-10-30, built | - | Browser check needs admin TOTP | - |
| S1 | School student record vs family child | separate record optionally linked / family child is the record | Separate school record, linked by family consent | All school administration | Learning in schools |
| S2 | Attendance / assessment scope | per day / per session; marks / report cards | Per-day attendance, then marks, report cards after a real term | Those modules | - |
| S3 | Fee policies | configurable per school | As recommended in the doc | Fees | - |
| S4 | Registrar/cashier | new roles / permissions on staff | Permissions on staff | Finance/registrar UIs | - |
| S5 | Online fee payment | manual first / per-school merchant | Manual first | Online fees | - |
| S6 | Legal for school-held child data | qualified review | Review before real school data | Real school records | Building with sample data |
| B1-B5 | Group billing, calendars, admins, receipts numbering, joining an organization | see `SCHOOL-MANAGEMENT.md` 8 | As recommended there | Their slices | Branch learning use |
| SB1-SB9 | Commercial policies | see `SUBSCRIPTIONS.md` 10 | As recommended there | Renewals, trials, promos, prices | Plan tooling |
| **New: N-1** | A calm weekly email summary to parents | none / weekly opt-in / weekly default | Weekly, **opt-in**, evidence-only, no "you'll lose" wording | That email | - |
| **New: R-1** | One account with two roles | two emails (today) / roles per account | Roles per account (design work: authorization by relationship already) | Teacher-parents' convenience | - |
| R-1a..R-1d (2026-11-03) | How R-1 is built | see `IDENTITY-PERMISSIONS.md` 4 | B: roles per account, one acting role per session; a principal may also hold TEACHER (R-1b, owner's call); ADMIN never combined (R-1c); a removed TEACHER role leaves authored content with its author (R-1d) | Building R-1 | Everything else |
| L-3 (2026-11-03) | Landing page FAQ and editable lists | words only (today) / items admins add, remove, reorder | An FAQ section and item lists in the existing site-copy model, EN/KM | Those sections | Editing today's words |
| SUB-1 (2026-11-03) | Landing page subjects | fixed three with hard-coded "ready" (today) / from published curricula | From published curricula, words still editable (close to P-1) | That section | - |
| **New: P-1** | Landing prices from plan records | copy (today) / from plan records | From plan records, copy for the words around them | Small change | - |

## 6. Roadmap (phases; no dates or costs invented)

**Phase 1 - Launchable family product (essential)**
- Objectives: real families can sign up, children learn real content in EN and KM, parents see true progress.
- Work: D-19 content (owner); launch operations (deployment, mail, monitoring, backups rehearsed, legal texts
  published, Khmer native review); landing prices from plan records (P-1); unsaved-guard for dialogs (**built** 2026-11-04); remove or
  park the unused streak backend; teacher/school first-run guidance.
- Acceptance: a stranger registers, verifies email, adds a child 3-12 and finds age-appropriate content in both
  languages; a full session plays on a phone over a mobile connection (**measured** 2026-11-04: see
  `SECURITY-AND-RELIABILITY.md`); prices shown equal what is charged; backups
  restore; legal texts accepted at registration.
- Decisions: D-19, P-1 (and D-20/D-18 recommended before launch, since they shape the child's experience).

**Phase 2 - Next release**
- Weekly parent summary (N-1), roles per account (R-1), support read-only view, renewals with notice (SB2), online
  payment once credentials exist, content import.

**Phase 3 - School administration** (after S1-S3, S6)
- Student records and transfers, attendance, assessments and report cards, school fees and receipts, announcements
  (bilingual), group reporting.

**Optional expansion**: trials and promotions (SB5/SB6), organization billing (SB1), offline play, public content
sharing (D-14).

## 7. Documentation discrepancies found

- `SCHOOL-MANAGEMENT.md` section 1 listed academic years as "Missing" though M12 P1 built them - **corrected**.
- Backend `streak` package and endpoint exist; `BUSINESS-REQUIREMENTS.md` (correctly for the product) says there are
  no streaks - the backend capability is dormant and undocumented as such - **recorded** here.
