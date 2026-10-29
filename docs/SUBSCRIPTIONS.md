# Subscriptions, plans and entitlements (living document, 2026-10-29)

How Bookeyond Kid sells access to the platform: who configures plans, what a plan controls, how a customer's
subscription lives, and what is built versus designed versus waiting for the owner. It extends
`PLATFORM-EXPANSION.md` §3 (the original model) and `docs/PAYMENTS.md` (how money is taken). It is the single
place for the **flexible plan management** requirement (owner brief, 2026-10-29).

> **Goal.** The business changes plans, prices, limits and offers by configuration in the admin interface, not by
> code changes. Access control, billing history and customer entitlements must stay reliable while it does.

---

## 1. Two money domains that never mix

| | **Bookeyond subscription billing** | **School fee collection** (future, `SCHOOL-MANAGEMENT.md` S6) |
|---|---|---|
| Who pays whom | A family, a school (branch) or an organization pays **Bookeyond** | A parent or guardian pays **a school** |
| For what | Access to the platform (plans, entitlements) | Tuition, transport, uniforms, other school services |
| Records | `subscription_plans`, `plan_entitlements`, `family_subscriptions`, `school_subscriptions`, `payment_orders`, `payment_events` | Its own fee heads, invoices, receipts and ledger (not built) |
| Numbering | Bookeyond order references (`BK…`) | The school's own invoice and receipt numbers, per branch |
| Who sees it | Platform administrators; the customer for its own records | The school's principal, cashier (S4 role) and the payer |
| Provider / account | Bookeyond's merchant account (ABA PayWay, bank transfer) | The school's own account(s), never Bookeyond's |

Shared code is acceptable only below the record level (money formatting, currency validation, a ledger trigger
pattern). There is no table, number sequence, permission or report shared between the two. A school's fee income is
never Bookeyond revenue, and a Bookeyond invoice is never a school fee.

## 2. What exists, what is incomplete, what is future

| Capability | State | Where |
|---|---|---|
| Plans created and configured by a platform administrator in the admin UI (name, description, audience, price, currency, monthly/yearly interval, self-service, limits) | **Built** | Operations → Plans; `SubscriptionService` |
| Plan lifecycle DRAFT → ACTIVE (frozen) → RETIRED; one default per audience | **Built** | `SubscriptionPlan` |
| **Versioning by immutability**: an active plan's price and limits never change; a payment order copies price, interval, limits and the plan's row version at checkout | **Built** | V42, `PaymentOrder.Offer` |
| **Duplicate a plan as a new draft version**, linked to the plan it came from (`based_on_plan_id`) | **Built (slice 1, this change)** | V79, `POST /admin/plans/{id}/duplicate` |
| **Subscriptions summary**: per plan and standing (in force, ending within 30 days, trial, payment overdue, cancelling, ended); families on the default plan (count only); schools without a paid plan; schools whose paid plan ends within 30 days | **Built (slice 1, this change)** | `GET /admin/subscriptions/summary`, Plans page "Subscriptions now" |
| Audiences: FAMILY and SCHOOL (a school = a branch) | **Built** | `PlanAudience` |
| Limit entitlements, enforced on the server where something is added, under a row lock, with a 409 that names the limit | **Built** | `EntitlementService`, `SchoolEntitlementService`, `PlanLimitException` |
| Admin grant / move end / end for families, with a reason, audited | **Built** | Plans → A family's plan |
| School plans start only on a confirmed payment; invoices, manual transfer confirmation (step-up + authenticator), refunds, reconciliation, ledger | **Built** | `docs/PAYMENTS.md` |
| Family self-service ask-for-a-plan (an administrator answers) | **Built** | Y11 |
| Upgrade/downgrade of a family by an administrator (give another plan; starts now) | **Built** | |
| Customer-facing upgrade path when a limit is reached | **Partial** | The refusal names the limit and the family's account page shows the plan and choices; schools are told to ask the Bookeyond team |
| Subscription history per customer | **Partial** | The audit log plus the payment ledger; no single timeline view |
| Organization (multi-branch) audience and organization-level subscriptions | **Designed, not built** (§5) | decision SB1 / B1 |
| Feature and module entitlements (on/off, not just limits) | **Designed, not built** (§4) | decision SB4 |
| Availability window (offer from / until) | **Designed, not built** (§6) | slice 2 |
| Renewal of a retired version (grandfathering) or move to its successor | **Designed, not built** (§7) | decision SB2 |
| Trials, introductory prices, promotions / discount codes | **Designed, not built** (§8) | decisions SB5, SB6 |
| Automatic renewal, renewal failure, grace period, dunning | **Not built** | Needs a live provider (ABA PayWay credentials) and decision SB3 |
| Tax / VAT on invoices, official tax invoices | **Not built** | Owner and accountant decision |
| Proration on a mid-period change | **Not built** | Recommendation: none at first (§7) |

## 3. Why a plan is configuration but a lever is code

Everything commercial about a plan is data: its name, words, audience, price, currency, interval, whether customers
can choose it, its limits, which plan is the default, when it is retired. None of these need a developer.

What a plan *can* control - its **levers**, `EntitlementKey` - is code, deliberately. A lever is only real if the
product checks it at the moment it matters (adding a child, inviting a teacher, uploading media). A lever stored
without an enforcement point would be a plan that promises something the product does not do. So:

- a new **plan** (or a new price, or new limits) is configuration;
- a new **lever** is a small code change: an enum value, its counting query, one `require…` call where the thing is
  added, its words in both languages, and a test. Adding a lever never changes existing plans: it is added to each
  plan with an "unlimited" value, so nobody loses anything.

There are no plan-name or plan-code checks anywhere in the application: every decision goes through
`EntitlementService` / `SchoolEntitlementService` (`effectivePlan`, `limit`, `require…`). The only plan codes in code
are the seeded defaults (V42 `EARLY_ACCESS`, V59 `SCHOOL_EARLY_ACCESS`), which are data the administrator can
replace by making another plan the default.

## 4. Feature and module entitlements (designed)

**Need.** Plans should be able to include or exclude whole capabilities ("report cards", "organization dashboard",
"multi-branch"), not just numbers.

**Design.** `EntitlementKey` gains a kind: `LIMIT` (today: a number or unlimited) or `FEATURE` (on/off, stored as
`limit_value` 0 or 1, so no schema change). `requireFeature(customer, key)` throws a `PlanFeatureException` (402 for a
school module the plan does not include, with the plan's name and an upgrade path); the UI asks the same service
what is included, so a hidden button is never the only protection.

**Rule that does not change.** A child's learning is never a plan feature: answering, evidence, mastery, Smart
Review, recommendations, parent insight about a child and a family's data export stay open to every plan
(learning integrity; the data is the family's). Plans may limit **how much** a family or school adds and **which
adult tools** a school uses, never the quality of a child's learning.

**Candidates (owner to choose, SB4).** School modules as they are built: attendance (P3), report cards (P4),
fee collection (P6, a school's own money domain), the organization dashboard (multi-branch §8.2), timetable. No
feature key is built until the module it would gate exists, so there is never a switch for something absent.

## 5. Schools, branches and organizations (designed)

The school model is now organization → branches (`SCHOOL-MANAGEMENT.md` §8). Billing must fit it without
rebuilding it, and must not force one model on everyone.

**Billing scope is a property of the plan** (`billing_scope`):

| Scope | Who owns the subscription | What it covers | Capacity | Typical customer |
|---|---|---|---|---|
| `BRANCH` (today) | one school row | that branch | the plan's limits, for that branch | an independent school; a group that wants each branch to pay itself |
| `ORGANIZATION` | the organization | the branches listed on the subscription (`subscription_coverage`) up to the plan's `BRANCHES_MAX` | **pooled** limits across covered branches (e.g. 600 children in all), optionally a per-branch cap set by the owner | a school group with central billing |

**Add-ons** are plans too: an `ADD_ON` plan with a price and a lever increment (one more branch, 100 more child
places), attached to a subscription for its remaining period. The customer's entitlement is the base plan plus its
add-ons, computed in one place.

**Resolution for a branch** (one rule, in `SchoolEntitlementService`): a branch's own paid subscription if it has
one in force; otherwise the organization's paid subscription if it covers this branch; otherwise unpaid. Limits are
checked against the branch's own counts (branch scope) or the organization's totals under a lock on the
organization row (organization scope).

**Changes of scope and their effect on access** (to be shown before confirming, and audited):
- *A branch is added to an organization subscription*: it is paid from that moment if a branch place is free;
  otherwise the owner is offered an add-on.
- *A branch is removed from coverage*: it keeps access until the end of the period already paid, then needs its own
  plan; nothing it holds is deleted (the same rule as an ended plan today).
- *A branch with its own paid plan joins an organization subscription*: its own plan runs to its paid end and is not
  renewed (no double billing, no refund needed); the switch is shown to the owner first.
- *An organization subscription ends*: every covered branch becomes unpaid at the same moment (Y44 rule), with
  the same warning and nothing deleted.

**Recommendation (SB1).** Keep `BRANCH` as the default and build `ORGANIZATION` when the first real group asks, as
the decision B1 in `SCHOOL-MANAGEMENT.md` already says. Pooled child places across branches are the simplest fair
measure for a group; a per-branch cap is optional.

## 6. Availability window (slice 2, designed)

An active plan may carry `available_from` / `available_until`. Outside the window it is not offered to new
customers (self-service choices, a principal's plan choices, an administrator's grant or invoice list); existing
subscribers are unaffected. This gives "offer from 1 January" and "this price until 31 March" without anyone
having to remember to retire a plan on the day. Small schema change (two nullable columns, a CHECK that until >
from) and one filter in the three places plans are offered.

## 7. Renewals, upgrades and downgrades

**Today.** A payment for the **same** plan extends from the current end (no lost days). Another plan starts now
and replaces the old one. A retired plan cannot be paid for, so a subscriber on a retired version renews through
an administrator (who invoices the successor).

**Decision SB2 - what happens at renewal when a plan has a newer version.** Options:
1. *Grandfather*: a subscriber may keep renewing the retired version at its agreed price until they change plan
   (generous; prices never rise for existing customers).
2. *Move to the successor at renewal*: the renewal offers the newer version (the one `based_on_plan_id` points
   from); the customer sees the new price before paying (honest; prices can rise, but only at renewal and with
   notice).
3. *Administrator decides per customer* (today's behaviour).

**Recommendation:** option 2 with a notice period (e.g. the new price is shown 30 days before the end), because it
keeps old versions from living forever while never changing a price inside a paid period. Nothing changes until
the owner chooses.

**Upgrades and downgrades.** Recommendation: an upgrade starts now and the remaining days of the old plan are
credited as extra days on the new one (no money refunded, no proration arithmetic on invoices); a downgrade takes
effect at the end of the paid period. Over a lower limit after a downgrade, the customer keeps everything and can't
add more (today's rule).

## 8. Trials, introductory prices and promotions (designed)

- **Trial (SB5)**: a plan may have `trial_days`. A trial is a subscription in `TRIALING` that grants the plan
  until its end; one trial per customer per plan family (lineage), recorded so it can't be repeated by
  re-registering the same school. At the end it becomes unpaid unless paid; nothing is deleted. Schools: a trial
  conflicts with the Y44 rule "only a confirmed payment opens a school", so it needs the owner's explicit decision.
- **Introductory price (SB6)**: modelled as a separate plan version with its own price and availability window,
  not as a discount field, so every order still copies one exact price.
- **Promotion codes (SB6)**: a `promotions` table (code, percent or amount, plans it applies to, window, use limit),
  applied on the server when the order is created and copied into the order; never typed into a price by a
  browser.

## 9. Lifecycle (target), with what is built

```
         ┌─ trial (SB5) ─► TRIALING ─┐
 new ────┤                           ├─► ACTIVE ─► (renewal paid) ─► ACTIVE …
         └─ payment / grant ─────────┘      │
                                            ├─► PAST_DUE (provider retry; grace, SB3) ─► ACTIVE | EXPIRED
                                            ├─► CANCELED (runs to period end) ─► EXPIRED
                                            └─► EXPIRED (ended by an administrator / period over / refund)
 EXPIRED ─► (pay or grant again) ─► ACTIVE   (reactivation; history kept)
```

Built: new, grant, payment, renewal of the same plan, replace, move end, end, refund reversal, expiry by date.
`TRIALING`, `PAST_DUE` and `CANCELED` exist in the schema and in the summary, but nothing sets them until trials, a
live provider and self-service cancellation exist.

## 10. Owner decisions (recommendations only; nothing is configured until approved)

| # | Decision | Recommendation |
|---|---|---|
| SB1 | Branch or organization billing for groups (= B1) | Branch by default; organization scope with pooled child places when a group asks |
| SB2 | Renewal of a retired version | Move to the successor at renewal, with 30 days' notice of the new price |
| SB3 | Grace period after a failed renewal | 7 days in `PAST_DUE` with full access, then unpaid; nothing deleted |
| SB4 | Which school modules become plan features | Decide per module when it is built; never a child-learning feature |
| SB5 | Trials (families, schools) | Families: not needed while the default plan is open. Schools: a 30-day trial only if Y44 is relaxed for trials |
| SB6 | Introductory prices and promotion codes | Introductory = a dated plan version; promotion codes only once there is a marketing need |
| SB7 | Currencies | USD for schools and families at first; KHR plans possible (amounts are stored in each currency's minor units) |
| SB8 | Prices, names and limits of the first paid plans (= PD-09) | Owner's commercial decision |
| SB9 | Tax on invoices | Owner with an accountant (Cambodian VAT registration) |

## 11. Phases

1. **Slice 1 (done, 2026-10-29)**: duplicate as a new version with lineage; subscriptions summary. Admin UI, audit
   (`PLAN_DUPLICATED`), integration and UI tests.
2. **Slice 2**: availability window; a single subscription timeline per customer (audit + payments).
3. **After SB2**: renewal to the successor version with notice.
4. **After SB1**: organization-scope plans, coverage, pooled limits, add-ons.
5. **With the first gated module (SB4)**: feature entitlements.
6. **After SB5/SB6**: trials, introductory versions, promotion codes.
7. **With live ABA PayWay credentials (SB3)**: automatic renewal, failed-renewal handling, grace period.

Each slice ships with server enforcement, both languages, tests and a browser check, and never changes an existing
customer's plan, price or history.
