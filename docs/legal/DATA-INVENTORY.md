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

## Added since 2026-10-27 (checked against the code and migrations, 2026-11-11)

- **Registration** (V87, V92): the email address is confirmed *before* an account exists - a verification row keeps the
  typed address, the requesting IP, link/grant hashes and timestamps, and the plan chosen on the plans page (if any). A
  paid plan chosen before registering is kept on the account (`plan_choices`) until it is paid or set aside.
- **Drawings** (V91): a drawing answer keeps the child's strokes (coordinates); parents and the class's teachers may
  add short written feedback to it. Included in the family export and in erasure.
- **Second tries** (V93): after a wrong answer the child's second answer (or "show me the answer") is kept beside the
  first. Included in the family export and in erasure.
- **Learning streaks** (STREAK-1): days on which the child finished real learning.
- **School student records and attendance** (V95, M12 - the school's own records, *separate* from families' child
  profiles and not linked to them): a student's name, the school's student number (optional), which class and since
  when (history kept), one attendance mark per day (present / absent / late / excused) with an optional note, and
  every change to a mark with the old and new values, who changed it and the reason. Entered by the school's principal
  and the class's teachers; seen only by them. **Notes may contain health or family information** (e.g. "unwell") -
  see question 11.
- **A school's own storage** (V94, M11): a school may keep its teachers' pictures and recordings in its own
  S3-compatible storage. Bookeyond keeps the address, bucket, access key ID and the secret key **encrypted**
  (AES-256-GCM, key from the environment); the files themselves are then with the school's provider.
- **Payments** today: bank transfer (confirmed by an administrator against the receipt) and invoices; the online
  provider (ABA PayWay) is built but not configured. No card details are collected by the product.

## Where data is hosted (from the deployment configuration, 2026-11-11)

- **Backend, database and uploaded media**: Railway (`docs/DEPLOY-VERCEL-RAILWAY.md`: backend service, Railway
  PostgreSQL, a volume at `/data` for media and the erasure ledger). **Region: [OWNER: confirm in the Railway
  dashboard - not recorded in the repository].** Measured 2026-11-11: requests enter Railway's Singapore edge but the
  backend answers ~300 ms later, so it is very likely *not* in Singapore (probably the US) - see
  `docs/HOSTING-AND-STORAGE-PROPOSAL.md`.
- **Web app**: Vercel (`frontend/vercel.json`), which also passes media requests through to Railway.
  **Region(s): [OWNER: confirm in the Vercel dashboard].**
- **Email**: Brevo SMTP (`smtp-relay.brevo.com`, per the deployment record). **Data location: [OWNER/REVIEW: Brevo's
  processing locations and data-processing agreement].**
- **SMS**: none (`SMS_SENDER` is `none`; D-04). **Online payments**: none configured.
- **A school's own storage**, when it connects one: that school's chosen provider and region (the school's choice).

## Cookies, browser storage, analytics, third parties

- **No cookies** are set by the app. **No analytics, advertising, tracking pixels or third-party scripts**; fonts and
  code are served by Bookeyond Kid itself.
- **Browser storage (localStorage)**, needed for the app to work: the sign-in refresh token and its expiry
  (`bk_refresh_token`), the interface language (`bk-locale`), sound on/off (`bk_sound_muted`), a linked child device
  (`bk.childDevice`), a principal's chosen school (`bk.principal.school`); and in **sessionStorage** (gone when the
  tab closes) a child's current sitting for break reminders. Signing out removes the refresh token. Nothing here is
  shared with anyone.
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
11. **School student records and attendance** (V95): the school is presumably the controller and Bookeyond its
    processor - confirm, with a school agreement (`SCHOOL-AGREEMENT-DRAFT.md`); retention of attendance and its
    corrections; whether free-text notes may hold health information and what that requires; what happens to a
    school's records when it stops its plan.
12. **Linking a school record to a family's child** (owner decision: only with the parent/guardian's explicit consent;
    not built yet): what that consent must say and record.
13. **A school's own storage** (V94): the school's provider becomes the school's processor for its media; what the
    school agreement must say about it.
14. **Cross-border transfers**: once the regions above are confirmed, whether transfers out of Cambodia need wording or
    safeguards.
