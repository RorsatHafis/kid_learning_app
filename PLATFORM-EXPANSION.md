# Bookeyond Kid — Platform expansion architecture (2026-09-29)

The decisions behind the product-completeness phase, written before building so no wave
creates a temporary system the next one has to throw away. Each section says what already
exists, what is decided, and what stays the owner's decision. Status per wave is kept in
`BOOKEYOND-KID-ENGINEERING-PROGRESS.md`.

## 1. Progression model: Subject → Journey → Chapters → Steps

**What exists.** A `Curriculum` is one subject for one age group; each published version is an
ordered list of learning objectives (`curriculum_objectives.display_order`). Enrolling pins that
version and seeds a learning path with the curriculum's activities in objective order; the
Adaptive Engine and Smart Review later insert practice, challenge and review steps.

**Decision.** Keep that model and make its structure visible, instead of adding a new
"level/world" entity:

| Child sees | Parent sees | Is, in the domain |
|---|---|---|
| a **subject** book on their shelf | a subject | `Subject` |
| a **journey** | a learning path | `Enrollment` (pinned `CurriculumVersion`) + `LearningPath` |
| **chapters** | learning objectives, with standing | `CurriculumObjective` → `LearningObjective`, in the educator's order |
| **steps** (books, lanterns inside) | activities, with why each was placed | `LearningPathItem` → `ActivityVersion` |

Why chapters are objectives, not a new structure:
- Objectives already carry the educational meaning, parent standings (secure / in progress)
  and skill coverage. A second grouping would drift from them and need its own authoring,
  versioning and migration.
- Educators already order them in the Content Studio's curriculum editor, so authors control
  the journey without new tools.
- Adaptive and review steps belong to the chapter of the skill they practise, so a child sees
  "more practice in this chapter". The adaptive logic still picks the next step; the structure
  only tells everyone where it sits.

A step's chapter is **derived** in the read model: the first objective, in curriculum order,
that maps one of the activity's skills. There is no schema change, and it works for every
existing enrolment.

Chapter state for the child: lit (every step done), current (holds the next step), or later.
Completing a chapter's steps is **not** called mastery. Parents keep seeing standings and
evidence separately (§13 of CLAUDE.md).

**Subjects.** A child with several journeys chooses from a shelf. Parents decide which subjects
a child is enrolled in; the child chooses among those. Only educators create subjects and
journeys. **No Khmer or English learning content is authored by the platform itself**
(PD-15: canonical content comes from educators). Subjects without a published journey are
shown honestly as not yet available.

## 2. Growing data: the right pattern per list

| List | Grows with | Pattern |
|---|---|---|
| Activity log (admin) | every event | keyset pages, filter by kind/account (done, ADM1) |
| Account history (admin) | per account | keyset "Show older" (done) |
| Accounts (admin) | families + staff | **exact email only** (done): families are never browsable, a support lookup needs the address |
| Staff (admin) | staff | server search by part of an email + role filter, pages of 25 (done, X3) |
| Studio questions | authoring | server search + type/latest-version/skill filters, pages of 25 (done, X3); the activity editor's picker searches published questions on the server |
| Studio activities | authoring | lessons of a subject, 10 per page; search matches lesson and activity titles (done, X3) |
| Studio subjects, curricula | tens | full list (small by nature) |
| Parent: children | a family | full list (a family is small) |
| Parent: recent activity | per child | "Show earlier" in pages of 10, keyset cursor (done, X3) |
| Parent: skills | per curriculum | grouped by chapter (bounded by the curriculum) |
| Child: journey | per path | chapters, with only the current chapter open |
| School: classes, teachers | a school | search once more than 12; otherwise full list |
| Class roster | a class (≈40) | full list with a name filter |

Server-side paging uses one shape everywhere: `?page=&size=` (size ≤ 100) returning
`{ items, page, size, total }` (`common/web/PageResponse`), except where rows are appended
continuously (audit, a child's history), which keep keyset cursors. Free text is matched as a
literal "contains" (`common/web/SearchText`: a typed `%` or `_` is only a character). In the UI,
filters and the page live in the address (`useUrlFilters`), so going back returns to the same page
of the same search; `SearchField` asks the server once typing pauses; `Pager` shows "26–50 of 312".
The school rows wait for the school wave (§4), where those screens change anyway.

## 3. Subscriptions and entitlements

**Nothing exists yet, and the business model is the owner's decision (PD-09).** What is decided
is the architecture, chosen so that prices, plan names, limits and the payment provider can be
set later without restructuring:

- **Plans** (`subscription_plans`): code, name, description, status (DRAFT/ACTIVE/RETIRED), an
  optional price per billing interval (nullable, since there is no pricing yet), and whether a
  family may choose the plan themselves.
- **Entitlements** (`plan_entitlements`): per plan, a typed key and value. Keys are the
  product's levers, defined in code: `children.max`, `subjects.max`, `subjects.catalog`
  (all / listed), `insights.parent`, `export.data`. A value can be "unlimited".
- **Family subscription** (`family_subscriptions`): one current row per family: plan, status
  (TRIALING, ACTIVE, PAST_DUE, CANCELED, EXPIRED), `current_period_end`, `cancel_at_period_end`,
  `source` (DEFAULT, ADMIN_GRANT, PROVIDER), and `provider` + `provider_reference` (empty until a
  provider is chosen). Every change is appended to `subscription_events` and audited.
- **Resolution** (`EntitlementService`): family → effective plan → entitlements. With no
  subscription, the family gets the platform's **default plan**, seeded as "Early access"
  with every entitlement unlimited, so nothing is taken away from anyone until the owner
  decides otherwise.
- **Enforcement points** are few and explicit: adding a child and enrolling a subject (409 with
  a plan reason, shown to the parent). Learning evidence, history and export are never locked
  (learning integrity; the family's data belongs to them).
- **Lifecycle** is a state machine in one service. An administrator can grant a plan for a
  period (pilots, schools, scholarships) and extend or end it. A parent can see their plan,
  its limits and its state, and cancel at period end once self-service plans exist.
- **Payments** sit behind a `BillingProvider` port (checkout session, webhook → subscription
  state). **No provider is implemented or faked.** The parent UI shows "not available yet" for
  paid upgrades instead of a pretend checkout.

**Built (X2).** V42 creates `subscription_plans`, `plan_entitlements` and `family_subscriptions`, and
seeds "Early access" as the default plan with no limits. Entitlements are `CHILDREN_MAX` and
`JOURNEYS_PER_CHILD_MAX`. Limits apply when a family adds a child or starts a journey, under a row lock,
as a 409 "Plan Limit Reached". A child is still created when a plan refuses its automatic enrolment.
Plans are DRAFT, then ACTIVE (frozen), then RETIRED, and at most one is the default. Administrators grant a
plan to a family (found by a parent's exact email), open-ended or dated, and end it; every change is
audited. Parents see their plan and its use under Account. There is no `subscription_events` table: the
audit log is the history, so there is no second record. A `BillingProvider` port was deliberately not added
before a provider is chosen (it would be an abstraction with no implementation). The integration point is
`FamilySubscription`'s transitions and the `PROVIDER` source.

Owner decisions this leaves open: plan names, prices, currency (USD/KHR), trial length, the
limits of a free plan (if any), the provider (e.g. Stripe, ABA PayWay, local wallets), refunds
and taxes.

## 4. Schools: real class work (PD-01, decided 2026-09-29 under the owner's delegation)

The owner asked for teachers and principals to do real school work and delegated the design.
The recommendation recorded under PD-01 is adopted:

- (a) A **class join code** made by the principal or the class's teacher: 8 characters, valid
  for 14 days, revocable. It only identifies a class; it grants nothing by itself.
- (b) A parent enters the code on their child's page, sees the school, the class and exactly
  what the school will see, and **grants** access (`school_child_access`, ACTIVE → REVOKED,
  audited). Only the family may grant or revoke.
- (c) A **confirmed email** is required to grant (PD-22).
- (d) The school gets **read-only** access to the child's name and to learning in curricula the
  school assigned. Family-chosen learning and parent insights stay private. Revocation ends
  access and enrolment immediately; the child's evidence is never deleted.

Teachers then get class learning views (roster, per-child progress in school-assigned
curricula, the class's skills) and can assign a curriculum to their class.

### Design (X4), written before building

**Schema (V43), three relationships, each explicit:**
- `class_join_codes`: a class's code (8 characters from an alphabet without 0/O/1/I/L), who made it,
  `expires_at` (14 days), `revoked_at`. At most one open code per class (partial unique index); making a
  new one revokes the old. The code identifies a class and grants nothing.
- `school_child_access`: the family's grant, per (school, child): `ACTIVE` → `REVOKED`, `granted_by`,
  `revoked_by`, timestamps. At most one ACTIVE row per pair (partial unique index); a later grant is a new
  row, so the history stays.
- `class_curriculum_assignments`: which curricula a class assigned, by whom (`ACTIVE` / `REMOVED`). It
  defines what the school may see, and what a child joining later is enrolled in.
- `class_enrollments` (existing) now counts only while the child's grant for that school is ACTIVE.
  Rows without a grant (TD-14, pre-PD-01) are ignored by every read, so no data migration is needed.

**Who does what:**
- The **principal** of the class's school, or a teacher **assigned** to the class, makes and revokes the
  code, sees the roster and learning, assigns curricula, and removes a child from the class. An **admin**
  does none of this: platform administration isn't a way into children's data (CLAUDE.md §10, §22).
- A **parent** of the child (family membership, confirmed email) previews a code (school, class, what the
  school will see) and joins, and can see and end the school's access at any time. Code lookups are
  rate-limited per account.
- Joining: the grant is created (or reused), the class enrolment made (or reactivated), and the child is
  enrolled in the class's assigned curricula, in one transaction, audited.
- Ending access: the grant becomes REVOKED, and the child's enrolments in that school's classes end, in
  one transaction, audited. The child keeps every journey and all evidence.

**What the school sees:** the child's display name; for each curriculum the class assigned, the
child's journey in it (steps done, chapter states) and skill standings with their evidence level.
This includes earlier work the child did in that same curriculum, and the parent is told so before
granting. Nothing else is visible: other journeys, the parent insights, the activity history, age,
family or account details.

**Plans:** a journey a school assigns is not the family's choice, so the family's plan never refuses
it. How school-assigned journeys count toward a family's own journey limit is an open question for
PD-09. It has no effect while the default plan has no limits.

**Built (X4).** As designed above, with these specifics: code lookups are limited to 10 per parent account
per hour; joining a class the child is already in changes and records nothing; the class may remove a child
(the family's grant stays theirs to end); assigning a class curriculum is class-staff only (no longer
admin). The parent's preview says exactly what the school will and won't see, including earlier work in an
assigned curriculum the family had already chosen. Open: school-assigned journeys vs a family's plan limit
(PD-09), school-wide reporting, grants in the family data export, and what a school sees after deletion (PD-24).

## 5. Content that can keep growing

- Educators keep authoring everything in the Content Studio. Nothing canonical is generated.
- The curriculum editor shows the journey as the child will meet it: chapters in order, each
  chapter's skills, how many published activities and questions back each skill, and gaps
  ("this chapter has no published activity for Blending sounds").
- Draft/publish/version rules stay as they are (pinned versions, retirement never breaks
  existing paths).
- Still open for the owner: content review before publishing (PD-20), withdrawing unsafe content
  (PD-04), and media and audio for literacy content. Khmer and English journeys need
  letter sounds and images, which the Studio can't attach yet.

## 6. Order of work

1. **Subject journeys with chapters** (child shelf, chapter path, parent subject management).
   This is the product's core promise, and the read model makes it safe.
2. **Growing data**: server paging and search where lists grow (§2).
3. **Subscriptions and entitlements** foundation (§3).
4. **School access and teacher class views** (§4).
5. **Studio journey builder**: chapter coverage and gaps (§5).
