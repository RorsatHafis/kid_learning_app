# School management - discovery, design and plan (M12)

**2026-10-28.** Discovery for M12, from the code at `master`, the roadmap's M12 design and research. It is a proposal:
nothing here is an approved business rule until the owner decides the items in section 5. Business reference:
[`BUSINESS-REQUIREMENTS.md`](BUSINESS-REQUIREMENTS.md); program record:
[`PRODUCT-IMPROVEMENT-ROADMAP.md`](../PRODUCT-IMPROVEMENT-ROADMAP.md).

## 1. What exists today, and the gaps

| Capability | State | Notes |
|---|---|---|
| School registration (self-service principal), school profile (name), archive/restore | **Live** | `School`, M4, X17 |
| Classes (name, archive), class join codes | **Live** | `SchoolClass`, `ClassJoinCode` |
| Teachers: invitation, acceptance, class assignment | **Live** | `SchoolStaff`, `TeacherAssignment` |
| Children in classes - **family children**, joined by the family with a code, with a per-child school grant | **Live** | `ClassEnrollment`, `SchoolChildAccess` (PD-01) |
| Class learning: roster, skills, trends, teacher-assigned practice | **Live** | learning-platform evidence only |
| Platform subscription billing (school pays Bookeyond): plans, invoices, manual bank/KHQR payment, admin confirmation with step-up, ABA PayWay provider (disabled) | **Live / built, limited** | `payment` package, Y44, M8 |
| Academic years, terms, school calendar | **Missing** | classes have no year; nothing to roll over |
| School-owned student records (student number, guardians, enrolment history) | **Missing - blocked** | needs decision S1 |
| Attendance, assessments, marks, grading schemes, report cards | **Missing - blocked** | scope decision S2 (owner) |
| School fees (school charges families), ledger, receipts | **Missing** | depends on S1; policy decisions S3 |
| Timetable, rooms, announcements, leave requests | **Missing** | later phases |
| Registrar / cashier / finance roles | **Missing** | decision S4 |

The platform today manages **learning in a school**, not the **school's administration**. Everything school-side
hangs off family-owned children that families choose to bring in.

## 2. Research - what established systems do (and what we take from it)

Sources (read 2026-10-28): RAITHub, *School management system build*
(https://www.raithub.com/blog/school-management-system-build) and *School fee management system*
(https://www.raithub.com/blog/school-fee-management-system) - an engineering blog by a vendor that says it has not
built one, so these are design recommendations, not proven practice; OpenEduCat finance docs
(https://newdocs.openeducat.org/features/core/financial/) - a real open-source ERP; AccessPH school system manual
(https://accessph.net/school-system/manual) - a real product's role index (Philippines). Cambodian calendar: public
schools broadly November-August (secondary source, https://www.aubsp.com/age-wise-education-system-in-cambodia/);
MoEYS Guideline No. 41 (2025-2026, primary) exists but its calendar text was not read - **verify**.

Common, well-founded practice:
- **One student record per school**, guardians linked with their own permissions; enrolment is per academic year/term
  and class, kept as history (transfers and withdrawals are new rows, not edits).
- **Academic years and terms are date ranges** set by each school; timetables and dated sessions hang off terms.
  Calendars differ between schools (public vs private/international), so nothing is hard-coded.
- **Attendance**: one mark per student per session/day, school-configurable codes, every correction audited (old
  value, who, reason).
- **Assessment**: assessments per subject and term with a maximum; grading schemes as data; published results change
  only through an audited correction (AccessPH: a dean approves grade changes).
- **Fees**: fee heads priced as data by year/grade/term; instalments in integer minor units; invoices generated from
  rules; discounts/scholarships applied as recorded, approved adjustments - never retroactively to paid invoices; an
  **append-only ledger** (charges +, payments/adjustments -, balance computed); one numbered receipt per payment;
  online payments credited only on server-side confirmation with a unique provider reference; refunds as reversing
  entries; cashier records payments, a bursar/principal approves waivers (separation of duties).
- Build order: daily-use records first (students, structure, attendance), then assessment/report cards, then fees and
  online payment (most testing), then integrations.

## 3. Recommended module map for Bookeyond Kid

1. **School structure** - school profile, academic years, terms (one current year), calendar days off; classes
   belong to an academic year; year-end rollover.
2. **Student records** (blocked: S1) - the school's record of a student: student number, name, date of birth, grade,
   guardians (contacts), status (applicant, enrolled, withdrawn, graduated), enrolment history per year/class;
   duplicate detection; spreadsheet import.
3. **Attendance** (blocked: S2) - daily or per-session register by class teachers, configurable codes, corrections
   with reason, monthly summaries, absence reasons from guardians.
4. **Assessment and results** (blocked: S2) - assessments per subject/term, marks entry, grading schemes, term
   results, report cards (draft -> published), audited corrections.
5. **School fees** (after 2; policies S3) - fee heads and schedules, invoices per student and term, ledger, cash and
   bank-transfer recording, receipts, statements, balances and aging, adjustments with approval; online payment
   later (ABA PayWay/KHQR exists for the platform's own billing and could be reused as a separate merchant).
6. **Communication** - school announcements to class or school guardians; delivery history.
7. **Learning integration** - the existing class learning (assigned curricula, skills, practice) linked to the
   student record **only** through the family's grant.

**Two money domains stay separate.** *Platform billing* (a school pays Bookeyond: plans, `payment_orders`,
platform invoices, platform admin) and *school fees* (a family pays the school: the school's invoices, ledger,
receipts, cashiers). Different tables, numbering, permissions, reports and payment accounts; a school's fee money is
never Bookeyond revenue. They may share code (provider adapters, idempotent event handling), never records.

## 4. Recommended workflows

**Student lifecycle** (when S1 is decided): register/import -> enrolled in a year and class (enrolment row) ->
transfer = end one enrolment, start another (both kept) -> withdraw/graduate = status change with date and reason ->
next year: rollover proposes each student's next class; the registrar confirms. Corrections are audited; records are
archived, never deleted while results or invoices reference them.

**Fee lifecycle**: configure fee heads and a schedule for a year/grade -> generate invoices for enrolled students
(numbered per school, idempotent per student+schedule) -> cashier records a cash or transfer payment with a reference
(duplicate reference refused) -> receipt numbered from a per-school sequence -> ledger balance; adjustments
(discount, scholarship, waiver) are new entries requiring a principal's approval -> refunds and reversals are new
entries, never edits -> reports: collected by head/class, outstanding and aging, adjustments by reason.

## 5. Decisions the owner needs to make (recommendation in bold)

| # | Decision | Options | Recommendation |
|---|---|---|---|
| S1 | **School student record vs family child** | (a) school records are separate and *optionally* linked to a family child by the family's grant (code or invitation); (b) the family child *is* the school record (today's model, no school-only data); (c) schools create child accounts that families later claim | **(a)**: the school owns its record (needed for attendance, results, fees of children whose families never use the app); linking is the family's consent, as PD-01 already works; no duplicate identities because a link joins two records rather than copying them |
| S2 | Attendance and report cards: in scope now? which levels? | per-day vs per-session attendance; marks only vs full report cards | **Per-day attendance first, assessments with marks next, report cards after one real term** |
| S3 | Fee policies | late fees on/off; who approves discounts; refunds allowed; currencies (USD/KHR) | **Configurable per school; adjustments approved by the principal; no automatic late fees at first; USD and KHR** |
| S4 | New roles | registrar and cashier as new platform roles vs permissions on school staff | **Permissions on school staff** (a teacher can also be the cashier): least privilege without new account types |
| S5 | Online fee payment | none, manual only; ABA PayWay per school (school's own merchant account) | **Manual (cash/transfer) first**; online only with each school's own merchant credentials |
| S6 | Legal | consent for school-held child data, retention of school records, receipts and tax rules in Cambodia (PD-31) | **Qualified review before real school data**; minimal fields until then |

## 6. Phased plan (completion criteria)

- **P1 School structure (now - no blocking decision)**: academic years and terms with one current year, validated
  dates, audited changes; classes belong to a year; principal UI. *Done when* a principal can set up a year with
  terms, mark it current, place classes in it, all server-checked, tested, and verified in the browser.
- **P2 Student records** (after S1): record, guardians, enrolment history, import, duplicate detection.
- **P3 Attendance** (after S2), **P4 Assessment and results** (after S2), **P5 Fees** (after P2 + S3),
  **P6 Announcements**, **P7 Online fee payment** (after S5).

## 7. Further ideas (ranked)

1. **Spreadsheet import with duplicate detection** (P2) - schools arrive with Excel lists; without import, onboarding
   fails. High value.
2. **Year-end rollover assistant** (P1 follow-up) - proposes next-year classes from this year's; prevents re-typing.
3. **Guardian absence notes** (P3) - parents explain an absence; the teacher sees it on the register.
4. **Fee reminders checked against the ledger** (P5) - never remind a family that has paid.
5. **Khmer/English receipts and statements** (P5) - families read them; KHR and USD.
6. **Low-connectivity attendance** (P3) - take the register offline and sync; many classrooms have weak data.
7. Later or excluded for now: timetable and rooms (useful, but primary schools often run fixed class teachers),
   transport, library, inventory, payroll (buy, don't build), multi-campus (until a real customer needs it).

## 8. Multi-branch schools (owner requirement, 2026-10-28) - foundation B0 built

Research (vendor material, read as claims): OpenEduCat, *Student information system for multi-campus groups*
(https://openeducat.org/solutions/student-information-system-for-multi-campus-groups/) - each campus an isolated unit
under a group layer; a stable student identity across the group; a transfer closes the campus record and opens a
continuation (no duplicate); campus-level fee structures within the group's policy.

**Model.** A **school organization** (a school or a group) has one or more **branches**. The existing `schools` row
*is* the branch: classes, academic years, staff, join codes, plan and payments stay on it, so nothing already built
changes meaning. To come, by rule:

| Belongs to the organization | Belongs to a branch (and a period) |
|---|---|
| Student records (one per student, stable ID - P2, decision S1), guardian links, the group's settings and policy defaults, group oversight and consolidated reports | Enrolments (branch + year + class), attendance, assessments and results, fee structures, invoices, receipts and **receipt numbering**, branch settings that override the group's, staff assignments |

- **Transfers between branches**: end the enrolment at one branch, start one at the other - the student record, past
  enrolments, results and the old branch's ledger stay where they were (finance never moves between branch ledgers).
- **Calendars**: each branch has its own; one branch's can be **copied** into another of the same organization (built);
  a shared group calendar with branch exceptions is possible later (decision B2).
- **People**: the organization's **owner** oversees every branch and adds branches (built); each branch has its own
  principal (today the owner; assigning another principal to a branch is next - decision B3); staff may work in
  several branches through separate assignments.

**Backward compatible (V78).** Every existing school became the only branch of its own organization (same name; its
principal as owner) - verified on the dev database: 123 schools, 123 organizations, none without. A school inserted
without an organization by any path (older code, raw SQL) gets its own from a trigger.

**Decisions for the owner (multi-branch)**

| # | Decision | Recommendation |
|---|---|---|
| B1 | Platform billing for a group: per branch (today) or one plan for the organization | **Per branch for now** (each branch waits for its own payment - as built); an organization plan later if groups ask |
| B2 | Shared group calendar vs branch calendars | **Branch calendars with copy** (built); a group calendar only if groups need one source of truth |
| B3 | Branch principals other than the owner; group administrators who aren't principals | **Invite a principal per branch** (the owner keeps oversight); a group-admin permission rather than a new role (S4) |
| B4 | Receipt numbering | **Per branch** (each branch's own sequence), with the branch's code in the number |

### 8.1 Roles across platform, organization and branch (owner's hierarchy, 2026-10-28)

| Level | Who | Can | Cannot |
|---|---|---|---|
| **Platform admin** (`ADMIN`) | Bookeyond staff | organizations and platform account status, Bookeyond subscriptions, platform configuration, audited support | children's data in classes (`requireClassStaff` excludes admins), a school's own business decisions |
| **Organization owner** | `school_organizations.owner_account_id` | create branches, **appoint/replace a branch's principal** (built, B1), run every branch's configuration (years, classes, staff, plan) through `requireSchoolManage`, switch branches with one account, group reports (to come: aggregates) | the children's data in a branch's classes unless they also run that branch (verified by test) |
| **Branch principal** | `schools.principal_account_id` | everything in their branch | other branches (verified: 403/404), organization settings, adding branches |
| **Branch staff** | teachers today (`SchoolStaff`, `TeacherAssignment`); registrar/cashier as permissions later (S4) | their assigned classes/duties, possibly in several branches | anything organization-wide by virtue of working in several branches |

No role combines platform and school authority. Every check is on the server (`SchoolAccessGuard`), keyed on the
record's own school/organization, so a changed URL or id finds nothing.

### 8.2 To build next, in order (each a complete slice)

1. **Organization dashboard** (owner) - **built 2026-10-29 (B2)**: `GET /api/v1/organizations/{id}/overview` (owner or
   platform admin; 404 for anyone else) and the owner's Branches section show each branch's classes, teachers,
   children with access, current year and payment state, and totals that count a child or teacher in two branches
   once (`OrganizationOverviewService`). Originally specified as: per-branch counts (classes, staff, children with access, plan state) - aggregates
   only, no child records.
2. **Shared settings with branch overrides** (when a module has settings: attendance codes, grading scales, fee heads).
3. **Student records (P2, after S1)**: `students` at organization level with a stable student number; enrolments carry
   branch + year + class; **transfer** = close one enrolment, open another (history and the old branch's ledger stay).
4. **A school joining an organization later** (design, not built): an owner invites an existing school's principal; on
   acceptance the school's `organization_id` moves to the inviting organization (its own one-branch organization is
   archived, not deleted); classes, years, staff, payments and audit history stay on the school row, so nothing is
   copied; student records (once they exist) move with an audited re-parenting, never merged silently. Needs the owner's
   approval (B5) because it changes who can oversee that school's data.

**B5 (new decision)**: may an existing school join an organization, and does its principal consent (recommended: yes,
by invitation accepted by the school's principal, audited, reversible by the platform admin).

**Subscription (B1, open; full design in `docs/SUBSCRIPTIONS.md` §5, SB1)**: options are per branch (today), per organization (one plan covering N branches), or per
student capacity across the organization. Recommendation: keep per branch until a real group asks; then an
organization plan whose entitlements are the sum of branch places, billed to the organization - a Bookeyond
subscription record, never mixed with school fees.

