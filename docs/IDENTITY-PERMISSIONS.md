# Identity and permissions - who may do what, and one person with several roles (R-1)

Part of the product review (`PRODUCT-JOURNEY-REVIEW.md`). **Verification level: INSPECTED** from code on 2026-11-03
(`PlatformRole`, `Account`, `AccountRegistrationWriter`, `ChildAccess`, `FamilyAccessGuard`, `CaregiverService`,
`ContentAccessGuard`, `SchoolAccessGuard`, `SchoolStaffService`, `SchoolOrganizationService`, the admin services, the
login/refresh token issue). The behaviours are covered by the existing authorization integration tests
(`SchoolAccessIntegrationTests`, the family/caregiver and content-tenancy suites); this pass did not re-run them.

## 1. How identity works today

- **One account, one platform role**: `accounts.platform_role` is PARENT, TEACHER, PRINCIPAL or ADMIN, set when the
  account is made and never changed. It is in the access token and decides which workspace the app opens.
- **How each role is made**: PARENT and PRINCIPAL by registration (a principal waits for approval); TEACHER by a
  school's invitation (a new email becomes a TEACHER account); TEACHER, PRINCIPAL or ADMIN by an administrator in
  Admin -> Staff (`POST /admin/staff`, authenticator step-up); the very first ADMIN by the startup bootstrap
  (`platform.bootstrap-admin`, once, when none exists). *(Corrected 2026-11-03: the first version said ADMIN came only
  from provisioning.)*
- **Children have no accounts**: a child is reached through a family membership or a caregiver grant (a child-device
  session is scoped to one child).
- **The role is a gate, never the permission**: after the role check, every resource is checked against a relationship.

## 2. Permission matrix (role gate + relationship that must hold)

| Capability | PARENT | TEACHER | PRINCIPAL | ADMIN | Relationship checked |
|---|---|---|---|---|---|
| See / manage a child, progress, insights | yes | no | no | no (no support view - A-1) | Family membership OWNER/GUARDIAN (`ChildAccess`, `FamilyAccessGuard`) |
| Caregiver access to a child | yes (accepting needs a PARENT account) | no | no | no | Caregiver grant, scope chosen by the family |
| Family plan, export, erasure | yes | no | no | admin confirms plan requests | Family ownership |
| Grant a school access to a child (join code) | yes | no | no | no | Family membership + class join code |
| Studio: write questions / activities / lessons | no | own content | own content | platform content | Ownership (`ContentAccessGuard`): teachers never edit system-owned content |
| Catalog (subjects, skills, curricula offered) | no | uses | uses | governs | Admin only for platform catalog |
| A class: roster, skills, practice, hardest questions | no | assigned classes | own school's classes | no | `SchoolStaff` accepted + teacher assignment, or principal of the school / organization owner; children only with the family's grant |
| School: classes, staff, years, plan, payment | no | no | own school | admin tools | `schools.principal_account_id`; organization owner sees branches' aggregates |
| Organization (branches, branch principals) | no | no | owner | yes | `organizations.owner_account_id` |
| Platform operations (accounts, plans, payments, content review, site copy) | no | no | no | yes + authenticator step-up | ADMIN role + TOTP step-up for sensitive actions; an admin can't suspend an admin |

## 3. One person, several roles - what happens today

| Real situation | Today | Effect |
|---|---|---|
| A parent who teaches at a school | The school invites their email -> **refused** ("This email address can't be invited as a teacher. Ask them for a different address.") | They need a second email and switch accounts |
| A teacher who is also a parent | Registering as a parent with the teacher's email -> refused (email taken) | Same: a second email |
| A principal who teaches a class | The principal sees every class of their school, but can't be **assigned** as a class's teacher (assignment needs a TEACHER account) | Works for viewing; not as "the class's teacher" |
| A teacher in two schools | One TEACHER account, staff membership in each school | **Works** (relationships, not roles) |
| A parent caring for another family's child | Caregiver grant on their own PARENT account | **Works** |
| An organization owner who runs one branch | Owner + branch principal on one PRINCIPAL account | **Works** |

The pattern: everything that is a **relationship** already composes on one account; only the **role** is single.

## 4. Options for R-1 (owner decision - not built)

| Option | What changes | Privacy / security | Cost |
|---|---|---|---|
| A. Two accounts (today) | Nothing | Strongest separation; a parent-teacher keeps two emails and two sign-ins | None; friction for teachers who are parents (common in Cambodian schools) |
| B. Roles per account, **one acting role per session** | `account_roles` (account, role, granted_by, granted_at, revoked_at); the token carries the acting role; a role switcher; each role gate reads "holds and is acting as" | Each request acts in one role, so school views never mix with family data; audit records the acting role; revoking a role ends its sessions | Migration + ~90 role-gate call sites move to one helper; invitation accepts a PARENT account by adding TEACHER; tests per gate |
| C. Linked accounts | Two accounts linked to one sign-in, switch between them | Same separation as A | Two of everything (passwords, 2FA, recovery) behind one door - more surface than B |

**Recommendation: B**, because authorization is already relationship-based: a role becomes "may act as", and every
relationship check (family, caregiver, school staff, class assignment, ownership) stays exactly as it is. Rules the
design must keep: a PARENT role never comes from a school (only registration); TEACHER from a school's invitation or an
administrator; PRINCIPAL keeps approval; ADMIN only from another administrator (or the first-run bootstrap) and never
combines with another role; switching role never
needs the password again but a sensitive admin action still needs step-up; revoking a school's last staff membership
does not remove the family role.

**Decisions needed before building (R-1a..R-1d):** (a) choose A/B/C; (b) may a principal also hold TEACHER to be
assigned to a class; (c) may ADMIN ever combine with another role (recommended: never); (d) what a revoked TEACHER role
leaves (recommended: authored content stays the author's, classes reassigned by the principal - as today when staff
leave).

## 5. Findings (to the roadmap)

| # | Finding | Severity | Proposed |
|---|---|---|---|
| ID-1 | A parent's email can't be invited as a teacher; the message says only "use a different address" | Medium (schools) | R-1 decision. The message stays generic on purpose: saying "a family account" would tell a principal what kind of account an email holds (privacy) |
| ID-2 | ~~No in-product way to add a second platform admin~~ - **wrong, withdrawn**: Admin -> Staff creates staff accounts including administrators (step-up, audited); DEPLOYMENT.md already says to add a second administrator there before turning content review on | - | Nothing to do |
| ID-3 | Role checks are written per service (`getPlatformRole() == ...`, ~90 sites) | Low now; cost for R-1 | If B is chosen, one `Actor` helper first, then move gates to it |
