# Bookeyond Kid - Business requirements (living document)

**First written 2026-10-27, last reviewed 2026-10-28, from the code at `master` and the project's records.** It explains what the platform is
for, who uses it, what it does today, the rules that govern it, and what is still undecided or unbuilt. It is the
business reference for the team; it does not replace:

- [`PRODUCT-IMPROVEMENT-ROADMAP.md`](../PRODUCT-IMPROVEMENT-ROADMAP.md) - current program, decisions **D-01..D-19**,
  dated log with verification levels;
- [`BOOKEYOND-KID-ENGINEERING-PROGRESS.md`](../BOOKEYOND-KID-ENGINEERING-PROGRESS.md) - engineering history, battles,
  product decisions **PD-01..PD-35**, technical debt;
- [`PRODUCT-MODEL-BRIEF.md`](../PRODUCT-MODEL-BRIEF.md) - business model and Cambodian context;
- [`docs/legal/DATA-INVENTORY.md`](legal/DATA-INVENTORY.md) - what data is collected, kept and shared;
- [`CLAUDE.md`](../CLAUDE.md) - the engineering constitution.

**Update this file** when a business rule, role, feature status or owner decision changes. Where the intended
behaviour and the implementation differ, both are written down.

**Status labels used below**

| Label | Meaning |
|---|---|
| **Live** | Implemented and verified at least by automated tests, and exercised in a browser or through the API (see the roadmap/progress entry for the exact level). |
| **Built, limited** | Implemented, but switched off, test-only or missing a real-world dependency (e.g. a provider account). |
| **Partial** | Some of the requirement exists; the gap is named. |
| **Planned** | On the roadmap, not built. |
| **Blocked** | Needs an owner decision or input first. |
| **Undecided** | Proposed behaviour exists only as a recommendation. |

---

## 1. Business and product overview

**Purpose.** Bookeyond Kid turns short learning activities into real evidence of what a child understands, decides
what each child needs next from their own answers, and explains it to the adults. Core thesis: *content tells what can
be learned, the teacher tells how, the platform decides what this child needs next*; the same content with different
evidence gives a different path and a different explanation.

**Who it is for.**

| Group | Role in the business |
|---|---|
| Children aged **3-12** | The learners. Cambodian families first; the youngest are pre-readers. |
| Parents and caregivers | The family customer: set up children, decide how the app is used, read the evidence. |
| Schools (principals, teachers) | The institutional customer: classes, teaching, oversight - with access to a child only when the family grants it. |
| Platform operators (administrators, educators) | Author and publish content, run plans, payments, accounts and the public site. |

**Value proposition.** For a child: warm, short, playful lessons in a world of their own (the River Garden, a Learning
Buddy they choose), with no ads, chat, rankings or engagement tricks. For a parent: an honest, evidence-based picture -
what the child can do, how strong the evidence is, why the next step was chosen, and how to help at home. For a
school: class learning drawn from the children's own answers, with family-controlled access.

**Business model (as implemented - [PRODUCT-MODEL-BRIEF.md](../PRODUCT-MODEL-BRIEF.md)).**

- **Families**: "Early access", nothing to pay, no payment details asked. Paid family plans are possible through the
  plan system (levers: number of children, journeys per child) but **no price is set** (PD-09, D-01/D-02 - no trial).
- **Schools**: plans are **arranged with the Bookeyond team** and given by an administrator (never self-service -
  server rule). Payment is manual: invoice, bank/KHQR instructions configured by the owner, receipt confirmed by an
  administrator with step-up authentication (M8). Value sold: places for children, teachers and classes; principal
  oversight. Storage is a fair-use safeguard (1024 MB media per teacher by default), not a product tier.
- **Never gated by plan**: a child's evidence, progress history, export or deletion.
- **No complimentary school access** (D-03). Sponsored access, if ever, uses the existing plans given for a period.

**Intended direction.** Expand subjects (Khmer, English, more maths) and age coverage across 3-12 with educator-authored
content (D-19, PD-15); a public launch once hosting, legal texts and providers are in place; school capabilities that
stay learning-focused (M12 scope undecided - no school-ERP by default).

---

## 2. The intended audience: ages 3-12 (owner decision, confirmed 2026-10-27)

Three different things, kept apart:

| Level | What it is | Today |
|---|---|---|
| **Platform audience** | Who Bookeyond Kid is designed for. | **Ages 3-12** - a deliberate product decision (D-09, confirmed by the owner 2026-10-27). Not to be narrowed to match the current content. |
| **Curriculum age range** | Each curriculum belongs to an **age hub** with a minimum and maximum age (`age_hubs`). A child is offered curricula for their age; a parent can choose otherwise (age-fit note, L-series). | One hub: **Junior, 4-9**. Admins can add hubs and curricula without code. |
| **Content available now** | What a child can actually learn today. | One published curriculum: **Maths - Junior** (addition: recognising sums, within 5, within 10, fluency) - the developer seed V29 (12 questions; D-19). Khmer and English are **in preparation**. Ages 3 and 10-12 have no content yet. |

**Rule for public claims:** say *who it is for* (3-12) and *what is available* separately and truthfully - e.g. "Made
for ages 3 to 12" plus "Early maths today; Khmer and English in preparation". Never imply lessons exist for an age or
subject that has none.

**Rule inside the product:** a child of any age 3-12 can be added. When no curriculum covers their age, adding the
child says so first, then offers paths made for other ages or "decide later"; the child's own space then has the
buddy explain that a grown-up needs to choose. No curriculum is assigned by default (Battle 2B).

---

## 3. Roles, responsibilities and permissions

Authentication proves identity; every action is also checked against the relationship to the specific resource
(family membership, school grant, class membership, teacher assignment, ownership, lifecycle). A UUID or a role is
never permission on its own.

| Role | Responsibilities | Can | Cannot |
|---|---|---|---|
| **CHILD** (no account) | Learn. | Use their own space through a parent's sign-in or a linked child device: path, activities, review, buddy, garden; switch sound; choose their buddy and language. | Sign in, upload, message anyone, see another child, leave the child space without the grown-up gate (press-and-hold). |
| **PARENT / caregiver** | Set up and oversee the family. | Create, edit, archive, delete children; choose curricula; set daily limits and break reminders; link child devices; invite caregivers (M10b); read skills, evidence, insights, recent activity, "why next"; grant or withdraw a school's access per child; export all family data; delete the account; manage email, phone, password, sessions. | See other families; change scoring or evidence; see school-internal data. |
| **TEACHER** (invited by a school) | Teach classes. | See classes they are assigned to and the learning of children in them (only with the family's grant); assign practice; author questions, activities and media in their school's scope; share with school colleagues by copy (D-13). | See family-private information, children outside their classes, other schools; publish to every school (PUBLIC deferred, D-14); edit system-owned content (4B). |
| **PRINCIPAL** | Run a school on the platform. | Self-register with a school (M4, email verified, no admin approval); create classes and join codes; invite teachers; see school-wide trends and plan/places; pay the school's invoice. | Grant themselves a plan; see a child without the family's grant; see family-private data. |
| **ADMIN** (platform) | Operate the platform. | Content Studio (all content), publication and take-down; staff and accounts (suspend/reinstate); plans, grants, invoices and payment confirmation (step-up with password + authenticator app, M8/M9a); storage allowances; public site copy, statements, demo lessons; audit log. | Use admin rights as a shortcut for ordinary family/school behaviour; see passwords or authenticator secrets. |

---

## 4. Feature inventory

Status as of 2026-10-27. "Where" points to the record with the verification detail.

### 4.1 Public site and trial

| Feature | What / why | Status | Where |
|---|---|---|---|
| Landing page | Explains the product to families and schools; shows the learning loop with the real components (F1d-1), evidence vocabulary as in the app (F1d-3), choosable buddies (F1d-4), plans as they are. No pricing, no invented testimonials (D-10). | **Live** | M2, F1d |
| Every public word editable | Admins override any landing/trust/try/parent-demo text in EN and KM; reset restores the original; audited. Product vocabulary (standings, buddy names, legal links) is deliberately not editable there. | **Live** | F1a, F1d-2 (V75-V76) |
| Try a lesson (`/try`) | Real published lessons, played exactly as a child plays them, checked by the server, **stored nowhere** - no learner record, evidence or mastery. Teach-first introduction, ending with "Create a family account" / "Not now". | **Live** | M2, F1b |
| Parent demo (`/try/parent`) | A fictional sample child (Mealea, 6), labelled as a sample; static, no request, nothing stored; numbers follow the real evidence rules. | **Live** | F1c |
| Brand statements / testimonials | Admin-managed; testimonials only with attribution, none exist. | **Live** (no testimonials) | M2 |

### 4.2 Accounts and identity

| Feature | Status | Notes |
|---|---|---|
| Registration (parent, principal + school) with email confirmation (real, single-use, expiring tokens) | **Live** | Email always required (D-15). |
| Sign-in, refresh-token rotation, reuse detection, session-family revocation, "sign out everywhere" | **Live** | Battles 4A, W4.2; PD-02/PD-03. |
| Password reset and change; email change | **Live** | Tokens keep the requesting IP. |
| Phone number on an account; sign-in and reset by verified phone | **Built, limited** | Only a local test sender exists - no SMS provider (D-04), so phone verification can't run in production. |
| Administrator authenticator app (TOTP) and step-up for money/access actions | **Live** | M9a, M8a. |
| Account suspension / reinstatement | **Live** | Admin. |
| Rate limiting on registration, sign-in, token refresh and account recovery | **Live** | Configurable (`RATE_LIMIT_*`). |
| Legal agreement at registration (versioned) | **Built, limited** | Shown only once the owner publishes texts and sets `LEGAL_*` (Y15); drafts exist (F2b). |

### 4.3 Family and child setup

| Feature | Status | Notes |
|---|---|---|
| Add a child (display name, date of birth, language, buddy) with curricula suggested by age | **Live** | No default curriculum is invented when none fits (Battle 2B). |
| Children's language (EN/KM) separate from the parent's | **Live** | Khmer is Claude-drafted, pending native review (PD-14). |
| Daily time limit and break reminders (10/15/20/30/45 min), graceful stop at a natural point | **Live** | M3; off by default (D-08/PD-33). |
| Child devices (a code links a device to one child) | **Live** | M10a; proposed limits D-05. |
| Caregivers (invite another adult into the family) | **Live** | M10b. |
| School access per child (grant / withdraw) | **Live** | PD-01. |
| Export the family's data; delete a child or the account (grace period, erasure sweep, erasure ledger) | **Live** | Default grace 7 days (max 90). |

### 4.4 Learning (child space)

| Feature | Status | Notes |
|---|---|---|
| River Garden path of books, activity introductions ("Learn first", L1), questions one per screen | **Live** | Seven question types: multiple choice, true/false, short answer, number, matching, ordering, sorting. |
| Hints | **Live** | A right answer after opening the hint is recorded as practice, not evidence (D-16, V74). |
| Feedback after each answer; correct answer shown after a wrong one | **Live** | PD-16; elaborated explanations exist only where authors wrote them (L2). |
| Educator recordings on questions/introductions | **Live** | Recorded in the Studio (L4). No text-to-speech (not adopted - see research README). |
| Completion with the next step; resume an unfinished activity | **Live** | Backend attempt state is authoritative. |
| Smart Review items on the path | **Live** | See section 6. |
| Garden of skills (a plant grows only from evidence) | **Live** | No points, coins, streaks or leaderboards. |
| Learning Buddy (five authored characters) | **Live** | Not a chatbot; names pending sign-off (PD-13). |
| Grown-up gate, mute, keyboard play, reduced motion | **Live** | |

### 4.5 Parent insight

| Feature | Status | Notes |
|---|---|---|
| Skills with standing + evidence strength, separately | **Live** | Section 6. |
| "Why this next?" in plain words from the recorded decision | **Live** | |
| Insights grounded in sufficient evidence; history of changes | **Live** | Battles 2C, 5C; PD-21. |
| Recent activity with hint use; "Helping at home" ideas | **Live** | L10-L11. |
| Age-fit note when a curriculum no longer fits the child's age | **Live** | Nothing changes unless the parent chooses. |

### 4.6 Schools

| Feature | Status | Notes |
|---|---|---|
| School registration (self-service principal), classes, join codes, teacher invitations | **Live** | M4. |
| Family-granted access; class learning and trends; teacher-assigned practice | **Live** | PD-01. |
| Plans with place limits; invoices; manual payment confirmation | **Live** | Y44, M8. KHQR image only once the owner configures it (D-07). |
| Teacher content and media, school sharing by copy | **Live** | M5, D-13. PUBLIC sharing deferred (D-14). |
| Linking a school's own student record to a family's child; attendance, exams, report cards | **Blocked** | M12 - owner decision; Battle 4C not designed. |

### 4.7 Content and administration

| Feature | Status | Notes |
|---|---|---|
| Content Studio: subjects, skills, objectives, questions, activities, curricula, preview, checks and gaps (L8), writing guide | **Live** | |
| Versioning: DRAFT -> PUBLISHED -> RETIRED; learners get published content only; exact pinned versions respected; take-down of a wrong version (Y16) | **Live** | Pinned versions that are later RETIRED (superseded) are still served by exact reference; DRAFT never is (Battle 3R, `PublishableVersion.wasPublished`). A wrong version is withdrawn separately (Y16). CLAUDE.md §25 still lists this as open - the code has resolved it. |
| Review/approval before publication | **Built, limited** | Mechanism exists, switched off (PD-20: who reviews). |
| Media upload (pictures, recordings) with per-teacher allowance | **Live** | Stored on the server disk (`MEDIA_ROOT`). |
| Storage-provider abstraction / school storage choice | **Planned / Blocked** | M11; needs D-06 and D-12 (library approval). |
| Plans and entitlements, grants, family plan requests | **Live** | Server refuses contradictory plans. |
| Audit log of administrative and sensitive actions | **Live** | Ids only; kept after erasure. |
| Public demo lesson picker, statements, site copy | **Live** | |

### 4.8 Languages and accessibility

English and Khmer for the whole interface, errors and emails (Y32), each person in their own language. **All Khmer is
Claude-drafted and must be reviewed by a native editor before real families** (PD-14, `KHMER-REVIEW-Y33.md`). WCAG 2.2
AA is an acceptance criterion: an automated axe gate runs on the main screens in CI (desktop and phone).

---

## 5. End-to-end workflows

1. **Visitor -> family.** Landing -> (optional) try a lesson or the parent demo -> register with email -> confirm email
   -> add a child (age suggests curricula) -> the child starts learning in their space. Nothing is collected before
   registration.
2. **Authentication.** Email + password; short-lived access token and a rotating refresh token kept in the browser;
   reuse of a refresh token revokes the whole session family; administrators add an authenticator code for step-up.
3. **A learning session.** Child opens the path -> next activity (teach-first introduction if authored) -> questions
   one at a time -> each answer is scored on the server against the canonical answer contract -> evidence is recorded
   (hint use flagged) -> mastery updates -> on completion the adaptive decision and Smart Review run in one
   transaction (idempotent) -> the next step appears, with its reason for the parent.
4. **Parent reading progress.** Family home -> child page: learning now and why, skills (standing + evidence), recent
   activity, insights, help at home; export or adjust settings.
5. **School.** Principal registers and creates classes -> invites teachers -> families join a class with its code and
   grant the school access per child -> teachers see class learning and assign practice -> the school's plan is
   arranged with the team -> administrator invoices -> payment confirmed -> places apply.
6. **Content.** Educator drafts skills/questions/activities in the Studio -> checks and preview -> publish (frozen
   version) -> curricula reference published versions -> retire or take down when superseded or wrong.
7. **Administration.** Staff and accounts, plans and grants, payments, storage, public site words, audit.

---

## 6. Learning model and recommendation rules

**Structure.** Subject -> curriculum (in an age hub) -> curriculum version -> objectives -> skills; activities
(versioned) exercise skills through questions (versioned). A child's **learning path** holds pinned activity versions;
items come from the curriculum, the adaptive engine, Smart Review or a teacher.

**Evidence and mastery** (server-authoritative, `EvidenceStandard`, `MasteryEngine`):

- Each scored answer without the hint is evidence for the skills the activity exercises; hinted answers are practice.
- Mastery: bias-corrected moving average, learning rate 0.2 (`MASTERY_LEARNING_RATE`).
- **Evidence strength**: none; partial (1-2 answers); sufficient (3+); strong (at the confidence saturation count,
  default 10).
- **Standing**: Not started; Too early to tell (<3 answers); Needs practice (mastery <0.6); Progressing; **Secure**
  (mastery >= 0.8 *and* evidence on **two different learning days**, D-17).
- Completion is never mastery; insights need sufficient evidence.
- **One answer per question per attempt.** A second answer to an already-answered question (another tab, a retry
  with a new key) is refused (409) and never becomes evidence; the screen then shows the answer that was recorded,
  with its verdict. Retries of the same submission are idempotent.

**Adaptive decision after an activity** (`AdaptiveEngine`; the weakest exercised skill governs, PD-11):

| Condition on the governing skill | Decision |
|---|---|
| mastery < 0.4 with >= 2 answers | **Reinforce**: the skill's first prerequisite, or the skill itself |
| mastery >= 0.8 with >= 3 answers | **Challenge**: a harder activity on the same skill |
| otherwise | **Continue** the planned path |

**Smart Review** (separate from the adaptive engine): a skill with >= 2 answers whose mastery falls below 0.5 is
scheduled for review and placed on the path. Deterministic - no AI decides learning.

**Open (D-18)**: whether something should bring a skill back on another day so it can be confirmed as Secure.

---

## 7. Business rules and constraints

- **Data ownership**: the family's learning data belongs to the family - export and deletion at any time, never gated.
- **Child privacy**: children have no accounts; minimal data (display name, date of birth; PD-35 suggests month/year
  only); no ads, analytics, tracking, cookies or third-party scripts; no child-to-child contact or user-generated
  content shown to children.
- **School access** is a family grant per child plus class membership plus teacher assignment - three separate
  relationships (PD-01); school access never exposes family-private information.
- **Consent**: today the parent's agreement at registration covers their children; a per-child consent model exists in
  code but is not used - lawyer question (DATA-INVENTORY q.2).
- **Content**: learners see published content only; drafts never leak; teacher content stays in its school (PD-05);
  canonical curriculum comes from educators, never generative AI (PD-15).
- **Learning integrity**: scores from the answer contract; evidence = the learner's actual response; one child's
  evidence never affects another's; demo play never touches learner records.
- **Wellbeing**: no engagement-maximising mechanics (streak loss, leaderboards, virtual currency, notifications to
  return); the app stops at natural points; parents set the rhythm.
- **Honesty**: no fake success states, no invented claims, testimonials or prices; "in preparation" where content isn't
  ready.

---

## 8. Integrations and operations

| Area | Today | Needed for launch |
|---|---|---|
| Email | SMTP, any provider; real tokens; a local mail shim in development | Owner's provider account (DEPLOYMENT.md) |
| SMS / phone verification | Test sender only | Provider choice and cost (D-04) |
| Storage | Local disk for media | M11 / D-06 / D-12 if object storage is wanted |
| Payments | Manual invoices and receipts; KHQR/bank instructions configurable | Owner's genuine instructions (D-07); prices (PD-09) |
| Hosting | Docker Compose; production files checked in CI; Vercel + Railway guide (`DEPLOY-VERCEL-RAILWAY.md`) | Owner accounts; not live |
| Team testing | Coworker preview on the owner's laptop through a Cloudflare quick tunnel behind a password gate ([COWORKER-PREVIEW.md](COWORKER-PREVIEW.md)) | - (the link changes when the tunnel restarts) |
| CI | Backend unit + integration, frontend lint/types/tests/build/audit, deployment files, end-to-end + accessibility on a Docker stack | - |
| Observability | Structured logs, correlation ids, health checks | Production monitoring and alerting (open) |

---

## 9. Limitations, open decisions and future requirements

**Owner decisions pending** (do not implement around them):

- **D-18** bring a skill back on another day to confirm "secure"; **D-19** who authors the real curriculum, in which
  languages, with what review; **M11** storage provider (D-06, D-12); **M12** school record <-> family child link and
  school-administration scope (Battle 4C).
- PD-09 prices; D-04 SMS provider; D-07 payment instructions; PD-13 buddy names; PD-14 native Khmer review; PD-20
  content review; PD-31/D-11 Cambodian PDP law status; PD-32 child-safety policy and contact; PD-34 logo files.
- Legal: privacy notice and terms are **drafts** (F2b) awaiting owner facts and qualified review; nothing is
  published or agreed until then. **No legal compliance is claimed.**

**Known gaps** (recorded, not hidden):

- Content: only the developer seed (12 bare addition items, no explanations or recordings) - the biggest product gap
  (D-19).
- Expired sign-in sessions and used tokens are not purged (retention question 4 in the data inventory).
- Phone verification cannot run in production without a provider.
- Tests still missing for some recent work (listed per entry in the roadmap; test files need authorisation).
- Two backend integration tests depend on data other test classes create, so CI can fail by test order
  (roadmap, 2026-10-27); a fix needs authorisation to change test files.
- No physical-device or screen-reader verification has been recorded.
- PRODUCT.md's "Capabilities" section predates several milestones; this document is the current inventory.

**Future requirements under consideration**: more subjects and age hubs across 3-12; native Khmer content with
native-voice audio; content review before publication; object storage; production hosting and monitoring; school
modules if approved; an evidence ladder with delayed checks (considered, not adopted - research README).
