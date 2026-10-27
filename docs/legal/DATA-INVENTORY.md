# What Bookeyond Kid collects, keeps and shares (F2 - facts from the code, 2026-10-27)

This is the factual basis for the privacy notice, the terms of use and any cookie or storage disclosure. It describes
what the code at `master` does; it is **not legal advice and not a policy**. Where the law or a choice is needed, the
question is listed at the end for the owner and a qualified lawyer. Update this file whenever behaviour changes - a
policy that disagrees with the product is worse than none.

## Who has an account

- **Parents / caregivers, teachers, principals, administrators**: email (required, D-15), password (stored only as a
  hash), interface language; a phone number only if they add and verify it (M9b). Administrators also hold an
  authenticator-app secret (M9a).
- **Children have no account and no email or phone.** A parent creates a child profile: a **display name** (what the
  child is called in their space) and **date of birth** (to suggest learning paths for their age). Children use the
  app through their parent's sign-in or a linked child device (a code; the device's user agent is kept with it).

## What is recorded while children learn

Every answer (the question, what was answered, right or wrong, time taken, whether the hint was opened - V74),
attempts, the skills' evidence and mastery estimates, Smart Review items, path decisions and the insights shown to
parents; the daily learning time used (M3 limits). Schools see a child's learning **only** when the family grants it
(PD-01) and the child is in their class.

## Other data

- **Sign-in sessions**: a hash of the refresh token, the **IP address and browser user agent**; account-action tokens
  (email confirmation, password reset, email change) keep the requesting IP.
- **Caregiver invitations**: the invited email address. **Families and schools**: their names. **Payments** (manual,
  M8): plan, amount, reference, a note; no card or bank details are collected by the product.
- **Media**: pictures and recordings that educators upload for lessons (stored on the server's disk, `MEDIA_ROOT`).
  Children upload nothing.
- **Audit events**: who (an id) did what to which record (an id) and when - kept after erasure; they name no one.
- **Public pages**: the public demo (`/try`) checks answers and stores **nothing**; visitors are not tracked.

## Cookies, browser storage, analytics, third parties

- **No cookies** are set by the app. **No analytics, advertising, tracking pixels or third-party scripts**; fonts and
  code are served by Bookeyond Kid itself.
- **Browser storage (localStorage)**, needed for the app to work: the sign-in refresh token and its expiry
  (`bk_refresh_token`), the interface language (`bk-locale`), sound on/off (`bk_sound_muted`), a linked child device
  (`bk.childDevice`), a principal's chosen school (`bk.principal.school`), and a child's current sitting for break
  reminders. Nothing here is shared with anyone.
- **Third parties that receive data** (only once the owner configures them): the **email provider** (SMTP: addresses
  and the emails' content); the **SMS provider** if one is chosen (D-04: phone numbers and codes; none today); the
  **hosting provider** of the server, database and backups (all data). The coworker preview's gate is the owner's
  local setup, not part of the product.

## Keeping and deleting

- A parent can **export** the family's data (account, children, answers with hint use, plan) and **delete** a child or
  the whole account. Deletion takes effect for learning at once (a child) or at the date (an account), and after a
  grace period (`ERASURE_GRACE_PERIOD`, default **7 days**, at most 90) an automatic sweep erases the profile, learning
  records, evidence, sessions, links and the account; an erasure ledger keeps restored backups from bringing it back.
- **Not cleaned up today**: expired sign-in sessions and used tokens (with IP and user agent) stay until the account
  is erased. See question 4.

## Consent as built

At registration an account agrees to the **current version** of the privacy notice and terms (Y15) - only once the
owner has published them and set `LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`, `LEGAL_VERSION`; a new version asks every
account again. **Until then nothing is shown and nothing is agreed.** A per-child consent record exists in the code
(`ConsentService`: data collection, terms) but is **not used**: today the parent's own agreement covers their children.

## Questions for the owner and a qualified lawyer

1. **Jurisdiction**: Cambodia's personal-data law is in draft (D-11) - which obligations apply now, and to families
   outside Cambodia if any can register?
2. **Parental consent for children's data**: is the parent's agreement at registration, plus adding the child, enough -
   or must consent be recorded per child (the unused `ConsentService`), with a verified adult?
3. **Children's age range 3-12**: any age-specific duties (e.g. under 13 elsewhere) given where families may come from?
4. **Retention of security records**: how long to keep expired sessions/tokens (IP, user agent). Recommended: delete
   them some time after they expire (e.g. 30 days after the 30-day refresh lifetime), keeping refresh-token reuse
   detection (Battle 4A) intact - an engineering change once the period is decided.
5. **Erasure grace period** (7 days) and **backups**: how long backups are kept and what the notice must say.
6. **Schools**: the school's role (controller/processor) for class data, and the family-granted access model (PD-01).
7. **Payments and receipts** (M8 manual): what payment records must be kept, for how long.
8. **Teacher and educator content**: licence for what they upload (pictures, recordings, lessons) - for the terms.
9. **Hosting location** (Railway/Vercel per DEPLOY-VERCEL-RAILWAY.md, not live): cross-border transfer wording.
10. **Language**: the notice and terms in Khmer and English - which version governs; native legal review of Khmer.
