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
| Accounts (admin) | families + staff | server search by email, page of 25 |
| Staff (admin) | staff | server search + role filter, pages of 25 |
| Studio questions | authoring | server search + type/status filter, pages of 25 |
| Studio activities | authoring | grouped by lesson; lessons paged; activity search |
| Studio subjects, curricula | tens | full list (small by nature) |
| Parent: children | a family | full list (a family is small) |
| Parent: recent activity | per child | "Show earlier" in pages of 10 |
| Parent: skills | per curriculum | grouped by chapter (bounded by the curriculum) |
| Child: journey | per path | chapters, with only the current chapter open |
| School: classes, teachers | a school | search once more than 12; otherwise full list |
| Class roster | a class (≈40) | full list with a name filter |

Server-side paging uses one shape everywhere: `?page=&size=` (size ≤ 100) returning
`{ items, page, size, total }`, except where rows are appended continuously (audit), which keeps
keyset cursors.

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
