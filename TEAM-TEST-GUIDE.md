# Team test guide - 2026-10-28

What changed since the last round (2026-10-20), what to try in each role, and how to report. Every item below is a
real feature; nothing here is a mock-up. Background on any item: the dated log in
[`PRODUCT-IMPROVEMENT-ROADMAP.md`](PRODUCT-IMPROVEMENT-ROADMAP.md); what the product is and does:
[`docs/BUSINESS-REQUIREMENTS.md`](docs/BUSINESS-REQUIREMENTS.md).

## Where to test

The **coworker preview**: the owner sends the link, a gate username and password, and the test accounts (parent,
teacher, two principals - one school paid, one not - and an administrator). The link changes whenever the preview is
restarted. Test data only: please don't enter real children's or families' information, and no real payments.

## Before signing in (a visitor)

- **Home page**: "How it works" plays one answer from the child's screen to the parent's view (pause it, or tap a
  step); in "A world of their own" tap a buddy. With reduced motion turned on in your device, nothing should move.
- **Try a lesson** (no account): a real lesson as a child plays it, with "Let's learn" first if the lesson has one.
  At the end: what an account adds, "Create a family account", "Try another lesson", "Not now". Nothing is saved.
- **Parents: see what you'd see** (`/try/parent`): a made-up sample child, Mealea - check it is clearly a sample.
- **Register as a parent**: the form says what you are asked for. Try both languages (ខ្មែរ button).

## Parent

- **Add a child** of different ages. The platform is for ages **3-12**, but today's content is early maths for
  **4-9**: for a child of 3 or 11 the app should say so honestly and still let you choose. Try a daily time limit and
  a break reminder.
- **A child's page**: skills with a standing (Too early to tell / Needs practice / Progressing / Secure) and a
  separate evidence line. A skill reads **Secure only after it has been shown on two different days**; until then it
  says Progressing - that is intended. "Why this next?" explains each step. Recent activity says when the hint was
  used; "Helping at home" gives ideas.
- **Family settings**: link a child's own device with a code; invite a caregiver (a second adult); give a school
  access to one child and take it back; download the family's data.

## Child (as the parent: "Start ... session")

- Lessons may open with **Let's learn** (a picture, words, sometimes a recording to listen to), then questions one
  at a time. On a phone the way forward should always be visible without scrolling.
- **Need a hint?** works as before, but a right answer after the hint now counts as practice, not as proof - the
  parent page shows it that way.
- Leaving the child's space needs a grown-up's long press on the lock. With a daily limit set, the child's space
  stops gently at the end of an activity.

## Teacher

- Studio: each activity shows what it is missing (introduction, explanations, recordings) and there is a short
  writing guide. Try **recording** an introduction or a question in the browser (allow the microphone).
- Upload a picture inside the question form. Share a lesson with your school; a colleague copies it to their own.
- Your class: children appear only when their family has granted the school access.

## Principal

- The unpaid school shows its payment panel (services wait for a confirmed payment); the paid one shows classes,
  teachers and its plan. Invite a teacher; create a class and its join code.

## Administrator

- **Operations -> Landing page -> "Words on the public pages"**: change any sentence of the public pages in English
  or Khmer, see it on the site, then reset it. Also choose which lessons visitors can try.
- Payments: confirm a test invoice (asks for your password again and the authenticator code). Plans, storage,
  staff and accounts, the activity log.

## Try to break it

Two tabs answering the same lesson; going back in the middle of a lesson; a slow or dropped connection; signing in
on two devices then "Sign out everywhere"; a very long child name; Khmer at a small phone width; anything that shows
one family another family's child.

## How to report

For each problem: role, page (copy the address), phone or computer, language, what you did, what you expected, what
happened, and a screenshot. Mark it **blocker** (can't continue), **wrong** (incorrect result or text) or **polish**
(works, could be better). Ideas are welcome too - say what you'd want and why.

## Known limits (not bugs)

- Content is the developer sample: a few addition activities with no explanations or recordings unless someone
  writes them (D-19 - who authors the real curriculum is undecided).
- Khmer interface text is a draft awaiting native review (PD-14).
- Phone verification and SMS can't send real texts (no provider yet, D-04).
- The privacy notice and terms are drafts and are not shown until published (F2b).
- A skill doesn't come back on its own on another day to be confirmed as Secure (D-18, undecided).
