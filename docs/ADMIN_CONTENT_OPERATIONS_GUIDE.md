# Bookeyond Kid — Admin & Content Operations Guide

This guide describes the **platform administrator's console and the Content Studio as they are built in this
repository**. It is kept in step with the code: when a screen or rule changes, this file changes in the same commit.
Where something is deliberately not built, it says so and names the decision it waits on (the `PD-*` / `OD-*` items
in `BOOKEYOND-KID-ENGINEERING-PROGRESS.md`).

> An earlier "Admin & Content Operations Guide" was referred to in a brief but was never in the repository. This is
> the first authoritative version, written from the implemented product (X15–X17), not copied from that document.

---

## 1. Who does what

| Role | Where they work | What they may do |
|---|---|---|
| **Administrator** | `/admin` | Run the platform: build and publish platform content, manage staff and accounts, see schools and the learning loop in counts, plans, reports, system status and the audit log. **Never** reads a child's name or learning — running the platform is no way into children's data (CLAUDE.md §10, §22). |
| **Principal** | `/principal` | Their own school: classes, teachers, what each class learns, its codes, and the learning of the children whose families joined (PD-01). |
| **Teacher** | `/teacher` | Classes they are assigned to: children's learning in what the class assigns, the class over time, follow-up and practice. **My content** (X22): their own questions, activities and curricula in the Studio, on the platform's catalog (read-only to them), and their own media. |
| **Parent** | `/parent` | Their own family and children only. |

Every rule below is enforced on the server. The screens only show what the server allows.

---

## 2. The administrator's console

The menu is grouped by purpose. Each area answers a different operational question.

| Area | Menu items | The question it answers |
|---|---|---|
| **Dashboard** | Dashboard | *What is waiting on me?* School applications, accounts locked after failed sign-ins, suspended accounts, plans ending soon — each linking to where it is done — then the platform and the content work in counts. |
| **Content** | Content Studio, Media library | *What can children learn, and is it ready?* |
| **People** | Staff, Accounts | *Who works on the platform, and is an account in trouble?* |
| **Operations** | Schools, Learning operations, Reports | *Are schools set up, is the learning loop running, where does content leave children with nothing to practise, how is the platform changing week to week?* |
| **Commerce** | Plans | *What can a family have?* |
| **System** | System status, Activity log | *Is the running system healthy, and who did what?* |
| — | My account | Your own sign-in, password and sessions. |

### 2.1 Dashboard
Read-only. Items appear only when something is waiting; "Nothing is waiting" is said plainly otherwise.

### 2.2 Content Studio
See §3. The Studio is where all learning content is created, versioned, checked, published and archived.

### 2.3 Media library (`/admin/media`)
Pictures and recordings used by questions, uploaded once and reused (X13).

- **Upload**: PNG, JPEG, WebP, MP3, M4A/AAC, OGG, WAV up to 5 MB. The server inspects the file itself (not its name)
  and refuses anything else with a plain reason. Identical bytes are stored once.
- **Find**: search by title or description; filter by kind, *in the library*, *not used anywhere*, *archived*.
- **Describe**: title, and *what it shows* — offered as the screen-reader text wherever the picture is used.
- **Usage**: every question version and field that uses the item (question picture, read-aloud, answer picture,
  answer recording), with its status and a link.
- **Archive / restore**: archived media leaves the library and pickers and can't be newly attached; published
  questions that use it keep it, untouched. Uploading the same file again restores it.
- **Delete**: only when nothing has ever used it (history points at used media).
- **Replacing a picture inside a published question** is deliberately impossible: published content is frozen. Make a
  new version of the question and choose the new picture there.
- Storage: files live under `MEDIA_ROOT` (see `DEPLOYMENT.md`). Back it up **together with** the database.

### 2.4 Staff
- **Principal applications**: a principal who applied is approved here; until then they can't set up a school.
- **All staff**: teachers, principals and administrators, searched and paged. **New staff account** creates a staff
  account with an emailed set-up link (no password is ever typed for someone else).

### 2.5 Accounts
Find any account by email; see its history (from the audit log); **suspend** (ends its sessions, a reason is
required and recorded) or **reinstate**. An administrator deletes nothing. When a family has asked to delete its
account (Y3), the panel says on which day it and its family will be erased - the family can still keep it until then.

### 2.6 Schools (X15)
Every school, its principal, its size (classes, teachers, children with access) and what is still to set up
(classes without a teacher or a curriculum, no classes yet, principal not active). Open a school to see each class's
teachers, number of children, curricula and whether a join code is open. **Counts and staff only — no child is named.**
Schools are set up and run by their principals; the console reads them (an administrator override of a school is a
product decision not taken).

### 2.7 Learning operations (X15)
- **The last 7 days of the loop**: children learning, activities finished of those started, answers and the share
  correct, activities left open for over a day, practice placed on paths by source (curricula, the adaptive engine,
  Smart Review, teachers), Smart Review items under review and due.
- **Content gaps**: skills an offered, published curriculum relies on that no published, in-use activity practises —
  children can't be given practice on them. Fix them in the Studio (add or publish an activity for the skill). The
  first 50 are listed by subject; the page says how many there are in all.
- **Age groups with nothing offered** to families.

### 2.8 Reports (X15)
Twelve weeks (Monday, UTC) of children learning, activities finished, answers, new families and new schools — one
small chart per measure on its own scale, the latest week as the headline, and the same figures as a table.

### 2.9 Plans (Commerce)
- **Plans**: create (code, name, description, limits), edit, **activate**, **retire**, **make default**. A family
  without a subscription is on the default plan. Limits apply only when a family adds a child or a journey — nothing
  already done is ever taken away.
- **A family's plan**: find a family by the parent's email; give it a plan, change its period end, or end it — each
  with a recorded reason.
- **Plan requests** (Y11): a plan marked *Families can ask for it* is shown to families, who may ask for it from their
  account page. Requests wait here (and on the Dashboard). **Open the family** and give the plan - that answers the
  request and emails the family - or **Decline…** with an optional message the family receives.
- **No payment is taken in the product.** Prices and a payment provider are an owner decision (PD-09); until then
  plans are given by an administrator.

### 2.10 System status (X15)
Build version, database schema version, profile, Java, uptime, memory, database size, media files and the free
space where `MEDIA_ROOT` lives (warns under 1 GB), the mail server and the public URL email links open. Settings are
named, never shown with secrets. Configuration is changed where the platform is deployed, not here.

### 2.11 Activity log
Every sign-in, approval, account action and content change, with who did it and when; filter by action.

---

## 3. Building content in the Studio

Content is built **subject by subject**, in this order. Each step only asks for what the next one needs.

```
Subject → Skills & objectives → Questions (+ media) → Lessons & activities → Curriculum (journey) → Validate → Publish
```

### 3.1 Subject
*Content Studio → New subject.* A code (stable, never changes) and a name. Everything else lives inside a subject.

### 3.2 Skills and objectives (the catalog)
*Subject → Skills & objectives.*
- **Skill**: something a child can be shown to be able to do ("counts objects to 20"). Evidence and mastery are kept
  per skill. A skill may have prerequisites (the journey builder warns when a prerequisite comes later).
- **Objective**: a teachable goal grouping skills ("Numbers to 20"). Curricula are made of objectives.
- Codes never change; names and descriptions can be edited (administrators only for platform content).

### 3.3 Questions
*Subject → Questions → New question.* Choose a type:

| Type | What the child does | Scored from |
|---|---|---|
| Multiple choice | picks one option (options may be pictures, with labels for screen readers) | the option chosen |
| True / false | picks true or false | the choice |
| Short answer | types a word | the exact accepted answer(s) |
| Number | types a number | numeric equality |
| Matching | joins pairs | every pair correct |
| Ordering | puts items in order (items may be pictures or recordings) | the exact order |
| Sort into groups | sorts 4–10 items into 2–4 groups ("Even" / "Odd"); items and groups may be pictures | every item in its own group |

A question can carry a **picture** and a **read-aloud recording** for its prompt, chosen from the media library or
uploaded in place. Tag each question with the skills it is evidence for. Questions are **versioned**: a draft can be
edited freely; a published version is frozen. To change a published question, make a new draft version.

### 3.4 Lessons and activities
*Subject → Activities.* A **lesson** groups activities. An **activity** is what a child plays: an ordered set of
published question versions, a difficulty and an estimated time, tagged with the skills it practises. Activities are
versioned like questions.

### 3.5 Curriculum (the journey)
*Subject → Curricula → New curriculum*, for an age group. A curriculum version is an ordered list of objectives; the
**journey builder** shows the chapters and steps a child will start with, how long each chapter takes, and issues:
**warnings** (a skill with no published activity, a prerequisite taught later) and **tips**. Practice and review are
added to a child's path as they learn (adaptive engine, Smart Review), not authored here.

### 3.6 Validate and publish

**When review is switched on (`CONTENT_REVIEW_REQUIRED=true`, PD-20).** Platform content isn't published by its author:
the Checks panel offers **Ask for review** instead of Publish. Another administrator finds the draft in **Waiting for
review** (Content Studio, and *Waiting on you* on the Dashboard), opens it and chooses **Approve and publish** - which
publishes it - or **Ask for changes…** with a note the author reads in the same place. A draft edited after the review was
asked can't be approved until its author asks again. A teacher's own content is never reviewed: it reaches only their
classes. Reviews need at least two administrators (Staff → New staff account → Administrator).

Every question, activity and curriculum version has **Check** (validation) and **Publish**. Publishing re-runs the
checks and refuses content a child couldn't actually play (no questions, an answer that can't be scored, a draft
question inside an activity, a curriculum step with nothing to play). **Nothing reaches a child until it is
published.** A published version is never changed afterwards; children who started a version keep it (version
pinning).

### 3.7 Preview
Question, activity and curriculum editors show a **child preview** — the content as a child will see it. (Developers
also have `/__play`, a development-only gallery of every interaction type; it is not in production builds.)

---

### 3.8 Teachers' own content (X22)
Teachers open **My content** (`/teacher/studio`) - the same Studio screens, scoped to them:
- They see the platform's subjects, skills and objectives but cannot change them (an administrator does).
- Their questions, lessons, activities and curricula are theirs alone: another teacher can't see or change them,
  and an administrator works on platform content. They may reuse **published platform questions** in their activities.
- Their curricula reach children only through **their class**: a published curriculum of theirs appears (marked
  "yours") in the class's *Assign a curriculum* list, next to the platform's. Their activities can then be placed as
  practice for the class's children. They never appear in the family catalog (N7).
- Their media library holds the platform's media and their own uploads.

## 4. Change, archive, restore — and why there is no "delete" for content

| Thing | Create | Edit | Archive | Restore | Delete |
|---|---|---|---|---|---|
| Subject | ✓ | name, description | ✓ (refused while it still has offered curricula) | ✓ | ✗ |
| Skill / objective | ✓ | name/title, description (code never) | ✓ | ✓ | ✗ |
| Question | ✓ | new draft version | ✓ | ✓ | ✗ |
| Lesson | ✓ | title | ✓ (refused while it has activities in use) | ✓ | ✗ |
| Activity | ✓ | title; new draft version | ✓ | ✓ (refused while its lesson is archived) | ✗ |
| Curriculum | ✓ | name; new draft version | ✓ | ✓ | ✗ |
| Media | ✓ | title, description | ✓ | ✓ | only if never used |
| School (principal) | ✓ | name | ✓ (archives every class; PD-26 as adopted) | ✓ (classes restored one by one) | ✗ |
| Class (principal) | ✓ | name | ✓ | ✓ | ✗ |
| Plan | ✓ | ✓ | retire | activate | ✗ |
| Staff / account | ✓ | — | suspend | reinstate | ✗ (PD-24 retention) |

**Archive means "no new uses" (PD-25).** Archived content is not offered to families, not newly enrolled or assigned
to a class, not placed on new paths (curriculum, adaptive engine, Smart Review, class practice — all read the same
single source), and can't be chosen for new content. **Nothing a child already has changes and nothing is deleted.**
An archived class (X17) takes no new children (its code is revoked), curricula, teachers or practice; its children
keep everything and its staff can still read it.

Why no delete: children's evidence, mastery and history point at the exact versions they answered. Deleting would
break that history. Retention and deletion of personal data is an owner/legal decision (PD-24).

Withdrawing an already-published version from children who have it is a separate, open decision (PD-04).

---

## 5. Schools and classes (principals)

1. A principal applies; an administrator approves them (Staff).
2. The principal sets up the school and **adds classes**.
3. They **invite teachers** by email; a teacher accepts and can then be **assigned** to classes.
4. A class **assigns curricula** — what every child in it learns.
5. The class makes a **join code** (14 days). A family enters it, sees what the school will see, and agrees: the school
   gets access to that child's school-assigned learning only (PD-01). The family can end it at any time.
6. **Manage class…**: rename, archive (see §4), restore. **Rename school…** on the school page.

School access, class membership and teacher assignment are three separate relationships and are checked separately.

---

## 6. Permissions in short

- A resource ID is never permission. Every read and write checks the relationship (family, school grant, class
  membership, teacher assignment, ownership).
- Administrators author **platform** content (no owner). A teacher's own content is theirs; another teacher's
  private content is invisible to them, and platform content is not teacher-writable.
- Administrators see schools and the learning loop only in counts. A class's learning is visible to its teachers and
  its school's principal only.
- Every lifecycle change (content, media, schools, classes, plans, accounts) is written to the audit log.

---

## 7. What content exists — and what must come from educators

The platform can build Khmer, English and Maths content of every type above, but **canonical curricula must be
written by educators and native speakers** (PD-14, PD-15). The repository's seed content is a small Maths path for
development and demonstration; it is not presented as a finished curriculum. Learning operations' **content gaps**
and **age groups with nothing offered** show where content is still needed.

---

## 8. Not built yet, on purpose

| Item | Waiting on |
|---|---|
| Payments | PD-09 (prices, provider); families can already ask for a plan (Y11) |
| Discounting evidence gathered from a version later taken down | PD-04 (taking a version down is built: Studio → the version → **Take it down…**) |
| Class challenges | a product decision (their unfinished API was retired in Y7; the data model stays) |
| Tracing and child voice recording | evidence-quality and privacy decisions |
| Report export (CSV) | not yet prioritised |
