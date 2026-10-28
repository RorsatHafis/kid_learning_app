# Payments (Y43)

How Bookeyond Kid takes payment for a plan, what is live today, and what remains before real money is charged.

**Status, 2026-10-20**

| Way of paying | State |
|---|---|
| Test simulator | Works in development and tests. **Refused by production**: the app will not start with it on. |
| Bank transfer / invoice (manual) | Works end to end once the owner supplies real payment instructions. Off until then. |
| ABA PayWay | Adapter written to ABA's published contract; **not live and never tested against ABA**. Needs merchant credentials and a sandbox test. |

Nothing in the product invents a price, bank account, merchant credential or successful payment.

## The rule

> The browser names a plan and a way of paying. The server decides everything else.

A request carries only `planId`, `method` and an idempotency key. The amount, currency, period and limits are
copied from the plan **on the server** into the order when checkout starts, so a later price change never
alters an order, and a tampered request can't set its own price (`PaymentIntegrationTests` sends a forged
amount and status and checks they are ignored).

Access is granted **only** when a payment is confirmed: by the provider's own answer, or by an administrator
against the real receipt. A pending, failed, cancelled or expired payment changes nothing.

## Schools pay for school services (Y44)

A school may use its school services (its workspace, classes, staff, invitations, class codes, rosters, school
learning and reports) only while **a subscription backed by a confirmed payment** is in force:

- a provider-sourced school subscription within its paid period, on an active school plan, **and**
- a succeeded payment order of the school whose paid period covers this moment.

Nothing else grants it: not the schools' default plan, not a plan an administrator gives (that action is now
refused - invoice the school instead), not a requested plan, a pending invoice, or a failed, cancelled, expired or
refunded payment. A test payment counts only where test payments are switched on, which production refuses.

Enforced on the server in `SchoolAccessGuard` (via `SchoolEntitlementService.requirePaid`) for principals and
teachers, and for a teacher accepting an invitation or a family joining a class. Refusals are `402` with
`schoolPaymentRequired`. Who may act is checked first, so a stranger still gets `403`.

Still open while unpaid: the school's plan, payment status, invoices and history for its principal, renewing the
school's own paid plan, administrators invoicing and reconciling, families leaving a school, and every family's own
learning (including path steps a class placed earlier). Nothing the school holds is deleted; services resume the
moment a payment is confirmed. A school's first plan is invoiced by an administrator (school plans aren't
self-service); its principal can then renew it.

## Lifecycle

```
            ┌──────────── cancel (customer) ────────────► CANCELLED
            │
 start ─► PENDING ── provider declines / admin rejects ──► FAILED
            │
            ├──────── not paid in time (sweep) ─────────► EXPIRED
            │
            └── confirmed (provider check / admin) ─────► SUCCEEDED ── refund recorded ──► REFUNDED
```

- **One pending payment per family (or school)** at a time, enforced by a partial unique index; another
  checkout is refused with a clear message until it is finished or cancelled.
- **Idempotent start:** the same idempotency key returns the same order (a double click or a retry after a lost
  reply creates nothing new).
- **Idempotent confirmation:** each provider message is recorded with its own id; the same confirmation again
  changes nothing (no second month, no second grant).
- **Late money:** a confirmed payment for an order that had been cancelled, failed or expired still counts.
  Money received is honoured rather than lost.
- **Amount check:** a provider answer whose amount or currency differs from the order is recorded as
  `AMOUNT_MISMATCH` and **not** applied; an administrator reviews it.
- **Expiry:** test and online payments expire after `PAYMENTS_PENDING_LIFETIME` (30 min); transfers after
  `P14D`. A sweep runs every 5 minutes (`platform.payments.sweep-interval`).

### What a confirmed payment grants

- A new plan starts now and runs one billing period (month or year, in UTC).
- Paying again for the **same** plan extends it from the current end (a renewal never loses paid days).
- The subscription records the provider and the order's reference; the plan change is audited
  (`SUBSCRIPTION_PAID`). An open plan request for that plan is answered automatically when an administrator
  confirms it.

### Refunds

A refund is recorded by an administrator **after** the money has actually been returned (the platform does not
move money back itself). It takes back exactly the period that payment bought; if nothing else remains, the
plan ends now. The order and its ledger keep the history. PayWay refunds are made in PayWay and are refused here
until ABA's refund notice is integrated. **Refund policy** (when, how much, partial refunds) is an owner
decision not yet made; the mechanism only supports whole refunds.

### The ledger

Every change appends a line to `payment_events`: what happened, from which state to which, by whom, and the
provider's message id. The table is **append-only**: a database trigger refuses updates and deletes, except
the anonymisation that family erasure performs under its session flag. No card data, secrets or raw provider
payloads are ever stored.

### Erasure

Erasing a family keeps its payment orders (the business record of money received) but removes the link to the
family and anything the customer wrote; ledger lines lose the actor's account id. How long anonymised payment
records must be kept is a **legal decision for the owner** (accounting retention in Cambodia).

## Who can do what

| Action | Who |
|---|---|
| Start a family payment, see its own orders, cancel, add a note ("how I paid") | A parent of that family |
| See a school's invoices, add a note | That school's principal |
| Simulate an outcome | The customer, **only** when the simulator is on (never in production) |
| List all payments, see the ledger, confirm a transfer (receipt reference required), reject, record a refund, ask PayWay | Platform administrators; every action is audited (`PAYMENT_CONFIRMED`, …). Confirming, rejecting and refunding need the password again and a code from the administrator's authenticator app (M8, M9a) |
| Invoice a school (a bank-transfer order at the school plan's price) | Platform administrators |

## Configuration

All under `platform.payments` in `application.yaml`, set through the environment:

| Variable | Default | Meaning |
|---|---|---|
| `PAYMENTS_PENDING_LIFETIME` | `PT30M` | How long a test or online payment may stay pending |
| `PAYMENTS_MANUAL_LIFETIME` | `P14D` | How long a bank transfer or invoice may stay pending while its receipt is awaited |
| `PAYMENTS_SWEEP_INTERVAL` | `PT5M` | How often payments past their lifetime are expired |
| `PAYMENTS_DEV_SIMULATOR_ENABLED` | `false` (`true` in the `local` profile) | Test payments. Production refuses to start when `true` |
| `PAYMENTS_MANUAL_ENABLED` | `false` | Bank transfer / invoice |
| `PAYMENTS_MANUAL_INSTRUCTIONS` | empty | The owner's own instructions shown to the payer (account name and number, what to quote). Manual payment is not offered while this is empty. M8b: an administrator can set the instructions in the app instead (Admin → Payments → "How customers pay by bank transfer", English and Khmer, with the bank's own KHQR image); once a version is saved there it takes over from these two variables |
| `ABA_PAYWAY_ENABLED` | `false` | ABA PayWay |
| `ABA_PAYWAY_BASE_URL` | ABA's sandbox | Switch to ABA's production host only after sandbox testing |
| `ABA_PAYWAY_MERCHANT_ID`, `ABA_PAYWAY_API_KEY` | none | Issued by ABA. Secrets: environment only, never in the repository, logs or responses |
| `ABA_PAYWAY_RETURN_URL`, `ABA_PAYWAY_SUCCESS_URL` | none | Where PayWay sends its notice (the backend's `/api/v1/payments/callbacks/aba-payway`) and where it returns the payer |

**Prices** are set per plan by an administrator (Plans page: amount in minor units, currency, month or year).
A plan without a price can't be bought; families can still ask for it, as before.

## ABA PayWay: what is built and what is not

Built from ABA's developer documentation (Purchase and Check Transaction, read 2026-10-20):

- The purchase form the browser posts to PayWay's hosted checkout, with the fields in ABA's documented order
  and the request hash: Base64 of HMAC-SHA512 with the merchant's API key (`AbaPaywaySigner`).
- **The notice PayWay sends back is treated as a hint only.** Its body and signature are not documented
  publicly, so it is never trusted: on any notice the server asks PayWay itself (Check Transaction, signed) and
  applies only that answer, with the amount and currency checked against the order.
- Status mapping from Check Transaction: 0 approved → succeeded, 3 declined → failed, 7 cancelled → cancelled,
  4 refunded → refunded, 2 pending → no change; anything else changes nothing.
- An administrator can ask PayWay about one order ("Ask PayWay now").
- The site's Content-Security-Policy allows the checkout form to post to ABA's sandbox host only.

Tested: request signing and response interpretation against hand-computed values (`PaymentContractTests`), and
that a forged notice changes nothing. **Not tested:** anything against ABA's servers.

Before PayWay can take real money:

1. ABA issues sandbox merchant credentials; set them in the environment (not in chat or the repository).
2. Run a sandbox purchase end to end: pay, notice, check, plan granted; then a declined and a cancelled one.
3. Confirm the exact production host from ABA, add it to the CSP `form-action` (nginx and `vercel.json`), and
   switch `ABA_PAYWAY_BASE_URL`.
4. Decide refunds (ABA refund API or manual) and the refund policy.

## Before charging families

- Owner decisions: prices, refund policy, terms of sale and receipts/invoices required in Cambodia, payment
  record retention after erasure.
- Real payment instructions (manual) or tested PayWay credentials.
- A deployed, verified production environment (see `DEPLOY-VERCEL-RAILWAY.md`).
