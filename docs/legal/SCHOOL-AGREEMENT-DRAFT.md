# School agreement - DRAFT, not for publication

> **Status: draft for the owner and a qualified Cambodian legal professional.** Written from what the product does at
> `master` (`DATA-INVENTORY.md`, 2026-11-11), not from legal knowledge. `[OWNER: ...]` marks facts only the owner can
> supply; `[REVIEW: ...]` marks points a lawyer must decide (the numbers refer to the questions in
> `DATA-INVENTORY.md`). Nothing here is to be shown to a school until both are resolved.

**Version** [OWNER] · **Applies from** [OWNER: date]
**Between** [OWNER: the operator's legal name, registered address and registration number] ("Bookeyond") **and** the
school named when it registers ("the School"), represented by its principal.

## 1. What Bookeyond provides

- A school workspace: classes, teachers, academic years and terms, the school's own student records and their daily
  attendance (M12), class learning with the content the School chooses, and the School's media library.
- Learning for children in the School's classes **only** where each child's family has allowed the School (the
  family's grant, which the family can withdraw). A family's own learning at home stays the family's.
- Storage for the School's teachers' pictures and recordings: Bookeyond-hosted within the plan's allowance, or the
  School's own S3-compatible storage if the School connects it.

## 2. Plan and payment

- The School chooses a school plan; its services open only once Bookeyond has confirmed the payment (bank transfer
  against the receipt, or invoice). A plan limits only what the School adds next; nothing the School already has is
  removed because of a plan.
- There is no free school plan. [OWNER: prices, billing period, renewal and what happens after a plan ends -
  `docs/SUBSCRIPTIONS.md` SB2/SB3.] [REVIEW 7: invoices, receipts and their retention.]

## 3. Roles for personal data [REVIEW 6, 11]

- **School student records and attendance** are the School's: the School decides what is recorded and by whom; the
  School is presumably the controller and Bookeyond its processor. [REVIEW 11]
- **Children's learning** in the School's classes is shared with the School by each family's grant; the family stays in
  control of it. [REVIEW 6]
- A School student record is **not** linked to a family's child profile unless the child's parent or guardian
  explicitly consents [REVIEW 12], and nothing of a family's learning history is merged into School records.
- The School's staff see only their own school's records; teachers only the classes they are assigned to.

## 4. The School's responsibilities

- Keep its staff accounts to the people who work there, and remove access when someone leaves.
- Record only what attendance needs; avoid health or family details in notes [REVIEW 11].
- If the School connects its own storage: choose and pay its provider, keep the access key valid, and arrange
  backups of what is stored there. Bookeyond keeps the access key encrypted and uses it only to store and read the
  School's teachers' media.

## 5. Security

- Data is reached only through the product's checks (school, class and teacher assignment); administrators of the
  platform do not open children's records to run the service. Changes to attendance are kept with who and why.
- [OWNER/REVIEW: incident notification - who is told, how and how fast.]

## 6. Where data is hosted

- Bookeyond-hosted data: Railway and Vercel, email through Brevo (`DATA-INVENTORY.md`). [OWNER: the regions in use.]
  [REVIEW 14: transfers out of Cambodia.]
- The School's own storage, if connected: the provider and region the School chose.

## 7. Ending the agreement

- [OWNER/REVIEW: what happens to the School's records and attendance when its plan ends or it leaves - an export for
  the School, how long Bookeyond keeps them, and deletion.] Today nothing the School has is removed because a plan
  ends; services pause until a payment is confirmed.

## 8. Law and language [REVIEW 1, 10]

- [REVIEW: governing law and courts; which language version governs (Khmer and English).]
