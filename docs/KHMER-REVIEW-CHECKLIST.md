# Khmer review checklist (PD-14) - everything a Khmer reader sees

> **Status: all Khmer in Bookeyond Kid was drafted by Claude and has not been reviewed by a native speaker.** It must
> not be offered to real families until this checklist is done. Generated from the code on 2026-11-11: **72 interface
> catalogs (3,777 Khmer lines)**, **12 server sources (511 lines: errors, emails, parent insights)** and **9 migrations**
> with seeded Khmer. `KHMER-REVIEW-Y33.md` has the context for the Learning Buddies and the River Garden text and can be
> used as part of item 1.

## How to review

- Corrections go into the files listed. **Keep the keys** (the English-side names); change only the Khmer values.
  `${name}`, `${n}` and similar are filled in by the app - keep them exactly.
- The child is addressed as **ប្អូន** everywhere in the child's space (Y32). Parents and staff are addressed politely
  and plainly.
- Keep the terms consistent across files - the current drafts use these; confirm or replace each one everywhere:

| English | Current Khmer | Where it matters |
|---|---|---|
| plan (subscription) | កញ្ចប់ | plans page, account, school plan |
| Free (the plan) | ឥតគិតថ្លៃ | plans, registration (owner: keep the name "Free") |
| skill | ជំនាញ | parent pages, Studio |
| mastered / secure | ស្ទាត់ជំនាញ / រឹងមាំ | parent pages |
| Smart Review | ការរំលឹកឆ្លាតវៃ | parent pages, child's path |
| hint | តម្រុយ | child's questions, parent pages |
| drawing | រូបគំនូរ | child, parent, class pages |
| attendance; present, absent, late, excused | វត្តមាន; មក, អវត្តមាន, យឺត, មានច្បាប់ | teacher's register |
| storage; bucket; access key | កន្លែងផ្ទុក; Bucket (ធុងផ្ទុក); Access key ID | principal's Plan page (provider terms kept in English with a Khmer gloss - confirm) |

- After editing: `cd frontend && npx vitest run src/i18n` (keys and placeholders match, Khmer isn't a copy of the
  English) and `cd backend && ./mvnw test -Dtest=ServerTextCoverageTests,ServerTextAssertCoverageTests`.
- Look at the screens in Khmer at a phone width (360 px) for clipped or overlapping text.

## What to review, in order

### 1. The child's space (highest priority - read by children)

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `features/buddy/buddy.messages.ts` | 17 |
| [ ] | `features/child/entrance.messages.ts` | 25 |
| [ ] | `features/child/garden.messages.ts` | 20 |
| [ ] | `features/child/learn.messages.ts` | 5 |
| [ ] | `features/child/messages.ts` | 215 |
| [ ] | `features/drawing/drawing.messages.ts` | 48 |
| [ ] | `features/learning/messages.ts` | 43 |

### 2. Public pages and signing up (first impression for families and schools)

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `features/auth/formField.messages.ts` | 1 |
| [ ] | `features/auth/messages.ts` | 117 |
| [ ] | `features/auth/phoneReset.messages.ts` | 16 |
| [ ] | `features/legal/messages.ts` | 12 |
| [ ] | `features/marketing/copyPreview.messages.ts` | 2 |
| [ ] | `features/marketing/messages.ts` | 113 |
| [ ] | `features/marketing/parentDemo.messages.ts` | 29 |
| [ ] | `features/marketing/trust.messages.ts` | 48 |
| [ ] | `features/marketing/try.messages.ts` | 30 |
| [ ] | `features/plans/plans.messages.ts` | 50 |

### 3. Parents

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `features/parent/account.messages.ts` | 82 |
| [ ] | `features/parent/ageFit.messages.ts` | 8 |
| [ ] | `features/parent/authenticator.messages.ts` | 31 |
| [ ] | `features/parent/breaks.messages.ts` | 36 |
| [ ] | `features/parent/caregivers.messages.ts` | 30 |
| [ ] | `features/parent/childDevice.messages.ts` | 29 |
| [ ] | `features/parent/familyDay.messages.ts` | 8 |
| [ ] | `features/parent/helpAtHome.messages.ts` | 6 |
| [ ] | `features/parent/messages.ts` | 330 |
| [ ] | `features/parent/overview.messages.ts` | 20 |
| [ ] | `features/parent/payments.messages.ts` | 47 |
| [ ] | `features/parent/phone.messages.ts` | 26 |
| [ ] | `features/parent/schoolLinks.messages.ts` | 9 |
| [ ] | `features/parent/setup.messages.ts` | 68 |
| [ ] | `features/payments/transfer.messages.ts` | 5 |

### 4. Schools: principals and teachers

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `features/principal/academicYears.messages.ts` | 38 |
| [ ] | `features/principal/branches.messages.ts` | 33 |
| [ ] | `features/principal/firstSteps.messages.ts` | 14 |
| [ ] | `features/principal/workspace.messages.ts` | 12 |
| [ ] | `features/school/attendance.messages.ts` | 52 |
| [ ] | `features/school/invoices.messages.ts` | 17 |
| [ ] | `features/school/messages.ts` | 168 |
| [ ] | `features/school/plan.messages.ts` | 44 |
| [ ] | `features/school/staff.messages.ts` | 111 |
| [ ] | `features/school/storage.messages.ts` | 36 |
| [ ] | `features/teacher/gettingStarted.messages.ts` | 16 |
| [ ] | `features/teacher/tools.messages.ts` | 3 |

### 5. Administrators and the Content Studio

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `features/admin/messages.ts` | 105 |
| [ ] | `features/admin/operations/faq.messages.ts` | 21 |
| [ ] | `features/admin/operations/landingContent.messages.ts` | 40 |
| [ ] | `features/admin/operations/messages.ts` | 147 |
| [ ] | `features/admin/operations/paymentInstructions.messages.ts` | 21 |
| [ ] | `features/admin/operations/payments.messages.ts` | 68 |
| [ ] | `features/admin/operations/plans.messages.ts` | 195 |
| [ ] | `features/admin/operations/schoolPlan.messages.ts` | 38 |
| [ ] | `features/admin/operations/siteCopy.messages.ts` | 25 |
| [ ] | `features/admin/operations/storage.messages.ts` | 55 |
| [ ] | `features/admin/studio/ageRange.messages.ts` | 13 |
| [ ] | `features/admin/studio/builder.messages.ts` | 86 |
| [ ] | `features/admin/studio/catalog.messages.ts` | 33 |
| [ ] | `features/admin/studio/editor.messages.ts` | 143 |
| [ ] | `features/admin/studio/form.messages.ts` | 74 |
| [ ] | `features/admin/studio/import.messages.ts` | 67 |
| [ ] | `features/admin/studio/introduction.messages.ts` | 16 |
| [ ] | `features/admin/studio/journey.messages.ts` | 71 |
| [ ] | `features/admin/studio/media.messages.ts` | 111 |
| [ ] | `features/admin/studio/messages.ts` | 235 |
| [ ] | `features/admin/studio/sharing.messages.ts` | 17 |
| [ ] | `features/admin/studio/storage.messages.ts` | 20 |
| [ ] | `features/admin/studio/subjectPlans.messages.ts` | 19 |
| [ ] | `features/admin/studio/writing.messages.ts` | 9 |

### 6. Shared interface words

| Done | File (`frontend/src/...`) | Khmer lines |
|---|---|---|
| [ ] | `components/stepUp.messages.ts` | 12 |
| [ ] | `components/ui/messages.ts` | 39 |
| [ ] | `i18n/switcher.messages.ts` | 4 |
| [ ] | `lib/api/messages.ts` | 23 |

### 7. Server text (shown to people: errors, emails, insights) - `backend/src/main/java/com/platform/...`

| Done | File | Khmer lines | What it is |
|---|---|---|---|
| [ ] | `admin/service/AdminService.java` | 5 | Emails: a staff account to set up |
| [ ] | `child/caregiver/CaregiverService.java` | 7 | Emails: a caregiver's invitation |
| [ ] | `common/web/ServerText.java` | 401 | Every error and refusal message, in Khmer |
| [ ] | `content/validation/ContentValidator.java` | 1 | Messages |
| [ ] | `family/erasure/FamilyErasureService.java` | 19 | Emails: a child or account will be / has been deleted |
| [ ] | `identity/phone/PhonePasswordReset.java` | 1 | Messages |
| [ ] | `identity/phone/PhoneVerificationService.java` | 1 | Messages |
| [ ] | `identity/registration/RegistrationVerificationService.java` | 11 | Emails: confirm your email to register |
| [ ] | `identity/service/AccountRecoveryService.java` | 35 | Emails: password reset, email change |
| [ ] | `parent/web/InsightText.java` | 8 | The parent insights' sentences |
| [ ] | `school/service/SchoolStaffService.java` | 9 | Emails: a teacher's invitation |
| [ ] | `subscription/service/PlanRequestService.java` | 13 | Emails: a plan request answered |

### 8. Khmer stored as data (seeded by migrations, editable by administrators afterwards)

| Done | Migration | What |
|---|---|---|
| [ ] | `V88__free_family_plan.sql` | The Free plan's Khmer name and description |
| [ ] | `V42__create_subscriptions.sql`, `V59__school_plans_and_storage.sql` | Early plans' Khmer text |
| [ ] | `V63__site_statements_and_public_demo.sql`, `V86__site_faq.sql` | Landing statements and FAQ seeds |
| [ ] | `V38`, `V39`, `V43`, `V45` | Seeded message text |

Administrators can correct plan names, the landing page's words and the FAQ in the admin area without a release.

## Owner inputs
- [OWNER: who reviews (a native-speaking editor), and by when.]
- [OWNER: the buddy names (PD-13) - see `KHMER-REVIEW-Y33.md`.]
- [OWNER/REVIEW: the legal texts' Khmer is part of the legal review, not this checklist (`docs/legal/`).]
