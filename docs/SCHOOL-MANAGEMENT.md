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
