# Bookeyond Kid — Owner's Guide

How to run Bookeyond Kid on your Windows computer and test the whole product yourself.
You don't need to use PowerShell, a terminal or any code: everything here is double-clicking
and a web browser.

---

## Part 1 — Running it

### What you need (once)

1. **Docker Desktop** for Windows (you already have it). It must be **running** — open it
   from the Start menu and wait until it says **Engine running**.
2. About **5 GB** of free space on drive C: for the first build.
3. This folder: `D:\Bookeyond-Kid`.

### Start

1. Open `D:\Bookeyond-Kid` in File Explorer.
2. Double-click **`Start Bookeyond Kid.bat`**.
3. **The first time only**, it asks for the administrator account you will use:
   - an email address (any address you like, e.g. `owner@bookeyond.test` — nothing is really
     sent, see *Emails* below);
   - a password of **at least 12 characters**, typed twice. The letters don't appear while you
     type; that's normal. Please don't use an apostrophe (`'`).

   It saves this in a private file `D:\Bookeyond-Kid\.env` on your computer only.
4. Wait. **The first start takes 5–10 minutes** (it builds everything). Later starts take
   under a minute.
5. When it says **Bookeyond Kid is running**, your browser opens **http://localhost:5173**.

### The two addresses

| Address | What it is |
|---|---|
| **http://localhost:5173** | Bookeyond Kid itself |
| **http://localhost:8025** | **Emails.** Every email the app sends (confirm your email, reset your password, teacher invitations) arrives here instead of a real inbox. Open it whenever a step says "check the email". |

Both only work on this computer.

### Stop

Double-click **`Stop Bookeyond Kid.bat`**. Everything you created is kept for next time.
Shutting down the computer is also fine.

### Start again from nothing

Double-click **`Reset Bookeyond Kid (erase all data).bat`** and type `ERASE`. This deletes
every account, child, answer and piece of content **in your local Bookeyond Kid only** (not
other Docker projects, not the code). Your administrator login is kept and recreated on the
next start. Then double-click **Start** again.

### If something goes wrong

| What you see | What to do |
|---|---|
| "Docker Desktop is not running" | Open Docker Desktop, wait for **Engine running**, double-click Start again. |
| "Bookeyond Kid did not start" | Double-click Start again (a first build sometimes needs a second try). If it still fails, keep the window open and send what it says. |
| The browser says the page can't be reached | Wait a minute and refresh; or check that nothing else uses port 5173 or 8025. |
| You forgot the administrator password | Use **Forgot your password?** on the log-in page; the reset email appears at http://localhost:8025. |
| You want a different administrator | Reset (above), delete the file `.env` in the folder, then Start: it asks again. |

---

## Part 2 — Test accounts

There are no pre-made accounts except your administrator: you create each person during the
tests below, exactly as real people would. Use made-up addresses — they all arrive at
http://localhost:8025. A simple pattern:

| Person | Suggested email | Password |
|---|---|---|
| Administrator | the one you chose at first start | yours |
| Parent | `parent1@bookeyond.test` | `Parent-Test-2026` |
| Second parent (another family) | `parent2@bookeyond.test` | `Parent-Test-2026` |
| Principal | `principal@bookeyond.test` | `Principal-Test-2026` |
| Teacher | `teacher@bookeyond.test` | chosen from the invitation email |

Passwords need at least 12 characters.

The app starts with one published learning path, **Maths - Junior** (6 activities about
addition, for ages 4–9). You can author more in the Content Studio (test 11).

---

## Part 3 — Testing the product

Each test says **what you do**, **what you should see**, and **what it proves**. Do them in
order the first time; later you can pick any. Tip: a private/incognito window lets you be two
people at once (e.g. the administrator and a parent).

### 1. The front page

- **Do:** open http://localhost:5173 and scroll to the bottom.
- **See:** the Bookeyond Kid home page; subjects show **Early Mathematics** as available and
  **Khmer** / **English** as *In preparation*; no prices; the footer links only jump within the
  page.
- **Proves:** the public page only promises what the product actually does.

### 2. A parent signs up and confirms their email

- **Do:** click **Get started**, keep **I'm a parent**, enter `parent1@bookeyond.test` and a
  password, create the account.
- **See:** "Your family" with no children yet, and a calm note asking you to confirm your email.
- **Do:** open http://localhost:8025, open **Confirm your email for Bookeyond Kid**, click the link.
- **See:** "Email confirmed". Open the same link again: it says the link **can't be used**.
- **Proves:** real email delivery; links work once only.

### 3. Adding a child

- **Do:** **Add your first child** — name `Sokha`, a date of birth making them 6–8 years old.
- **See:** Sokha's card with **Maths - Junior** and *Next: Addition Recognition Practice*.
  Click Sokha: **Learning now** shows *Up next* and **Why this next?**
- **Proves:** a child is placed on the published path for their age, and the parent is told why.

### 4. The child's space

- **Do:** on Sokha's page click **Start Sokha's session**, then **Play**, then **Let's go**.
- **See:** a friendly question (e.g. `1 + 1 = ?`), the Learning Buddy, a sound button at the top.
- **Do:** type a **wrong** answer, press **Check**.
- **See:** a gentle message and the right answer — never a scolding.
- **Do:** press **Next** and answer correctly.
- **See:** a cheerful confirmation; at the end a completion screen with what comes next.
- **Also try:** reload the page in the middle of an activity — it continues where you were.
  Turn the sound off with the speaker button. Use only the keyboard (Tab, Enter, and number keys
  on choice questions).
- **Proves:** the child can learn, recover from interruptions, and use a keyboard.
- **Also try:** **My shelf** (on the child's home): a lantern for each thing achieved (first book,
  a chapter, a whole path), *I can…* and *Getting stronger* skills from the answers, every book lit
  and still waiting, and **Play again** on a lit book (practice outside the path; its answers still
  count as evidence). When every book is lit, the home offers the shelf instead of a dead end.

### 5. Leaving the child's space

- **Do:** press and **hold** the **Grown-ups** button for about a second and a half.
- **See:** you're back in the parent area. A quick tap does nothing.
- **Proves:** a young child can't wander into the parent area by accident.

### 6. What the parent learns

- **Do:** play two or three more activities as Sokha (mix right and wrong answers), then open
  Sokha's page as the parent.
- **See:** **Skills & evidence** — each skill with a standing such as *Too early to tell*,
  *Progressing* or *Secure* and how many answers it's based on; **Recent activity** with each
  activity's right/total and the decision taken next; insights only once there's enough
  evidence.
- **Do:** click a skill's name (in *Skills & evidence* or *What changed*).
- **See:** where it stands and the numbers behind it, each activity with its answers on that skill
  and the standing after it, and what is waiting on the path for it and why.
- **Proves:** parents see evidence, not just "completed", and claims never go beyond the answers.

### 7. The parent's account

- **Do:** open **Account**.
- **See:** your details, confirmed email, **Change email**, **Change password**, **Devices**
  (every signed-in browser, with **Sign out** for the others and **Sign out everywhere**),
  **Your plan**, **Your data** and **Delete account**.
- **Do:** click **Download my family's data**.
- **See:** a file `bookeyond-kid-data-<date>.json` with your account, Sokha, and every answer.
- **Do:** change your password (you stay signed in here); then **Sign out everywhere**.
- **See:** you're signed out and must log in with the new password.
- **Also try:** **Change email** (the link goes to the new address; your old address is told);
  on a child's page, **Delete {child}…** (the child leaves learning straight away and is erased
  after a waiting period - 7 days by default - during which **Keep {child}** on the family page
  undoes it); **Delete account** (everything keeps working until the date, and **Keep my
  account** undoes it). In **Your plan**, **Ask for** a plan the administrator has offered.
- **Proves:** families control their own account, can take their data with them, and can have it erased.

### 8. Forgotten password

- **Do:** log out; click **Forgot your password?**; enter `parent1@bookeyond.test`.
- **See:** the same neutral message whatever address you type (so nobody can test which
  addresses have accounts). At http://localhost:8025 a **reset** email appears.
- **Do:** open its link, choose a new password.
- **See:** "Your password has been changed and every device was signed out." Log in with it.
  The old password no longer works; the reset link can't be used twice.
- **Proves:** a safe, real recovery flow.

### 9. Families are kept apart

- **Do:** copy the address of Sokha's page. In a private window, register
  `parent2@bookeyond.test` and paste that address.
- **See:** "We couldn't find this child in your family…" and nothing about Sokha.
- **Proves:** one family can never see another family's child, even with the exact link.

### 10. The administrator's console

- **Do:** log in with your administrator account.
- **See:** the admin console, grouped: **Dashboard**; **Content** (Content Studio, Media library);
  **People** (Staff, Accounts); **Operations** (Schools, Learning operations, Reports); **Commerce**
  (Plans); **System** (System status, Activity log); My account. The full guide is
  [docs/ADMIN_CONTENT_OPERATIONS_GUIDE.md](docs/ADMIN_CONTENT_OPERATIONS_GUIDE.md).
- **Do:** open **Activity log**.
- **See:** everything that has happened — sign-ups, confirmations, password changes — newest
  first, filterable, with **Show older**.
- **Proves:** operations are visible and recorded permanently.

### 11. Authoring content (Content Studio)

- **Do:** Content Studio → add a subject (e.g. `Counting`). In **Skills & objectives**, add a
  skill and an objective, and link the skill to the objective.
- **Do:** **Questions** → Type *Number answer*, write a question, its answer, tick the skill,
  **Create draft**. Look at **Checks** and **Preview** (exactly what a child sees), then **Publish**.
- **Do:** **Activities** → add a lesson, **+ Activity**, tick the skill, **Create activity**.
  Notice **Publish** is disabled until the activity has a question. Add your question,
  **Save questions**, **Publish**.
- **Do:** **Curricula** → name it, age group *Junior*, **Create draft**, add your objective,
  **Save order**, **Publish**.
- **Do:** as a parent, add a new child (or use Sokha's **Add a subject**) and choose your path;
  play it as the child.
- **See:** the child gets exactly the question you wrote and is marked by your answer.
- **Also try:** the other question types — multiple choice (choices can be pictures), true or
  false, short answer, matching, *Put in order* and *Sort into groups* — with a picture and a
  recording on the question; the **Media library** to reuse uploads; **Manage…** to rename,
  archive (no new uses; nothing a child has changes) and restore anything in the Studio.
- **Proves:** an educator can author, check, publish and deliver real content — no code.

### 12. Staff and schools

- **Do:** in a private window, register choosing **I'm a principal** (school name,
  `principal@bookeyond.test`).
- **See:** "Application received". Logging in is refused for now.
- **Do:** as administrator → **Staff** → **Principal applications** → **Approve**.
- **Do:** log in as the principal: set up the school, add a class (e.g. `Grade 1 A`), invite
  `teacher@bookeyond.test`.
- **Do:** at http://localhost:8025 open the invitation, click the link, choose the teacher's
  password, log in as the teacher and **Accept** the invitation.
- **Do:** as the principal, open the class and assign the teacher.
- **See:** the teacher now sees the class.
- **Do:** as the teacher (or principal), open the class, assign a curriculum and **Make a code**.
  As a parent (with a confirmed email), on the child's page choose **Join a class**, enter the code
  and agree.
- **See:** the child is in the class; the teacher sees the class's learning (only in what the
  class was assigned), *The class over time*, and can open a skill to see who needs practice and **Assign practice**.
  The family sees who asked and why, and can end the school's access at any time.
- **Do:** as the principal, look at **Learning across the school**; open a class and use
  **Manage class…** to rename or archive it (its code stops; its children keep everything).
- **Do:** as the teacher, open **My content**: the same Studio, for your own questions, activities
  and curricula (the platform's skills are read-only to you). A curriculum you publish can be
  assigned to your class (it is marked *yours*).
- **Proves:** schools, staff, classes and family consent work end to end, with every step authorized.

### 13. Suspending and reinstating an account

- **Do:** as administrator → **Accounts**, find `parent2@bookeyond.test`, **Suspend account…**,
  give a reason, confirm.
- **See:** "Suspended". In parent2's window, reload: they're signed out and can't log in.
- **Do:** **Reinstate account…** with a reason. parent2 can log in again. Both actions appear
  in the account's history and the Activity log.
- **Proves:** a compromised or departed account can be stopped at once, and it's audited.

### 14. Boundaries between roles

- **Do:** as a parent, type http://localhost:5173/admin/accounts in the address bar.
- **See:** you're taken to the front page, not the admin console.
- **Do:** as the teacher, type http://localhost:5173/parent.
- **See:** the front page again — a teacher has no family area and no children's data.
- **Proves:** each role only sees what it should. (The server refuses these requests too; the
  page redirect is only the friendly part.)

### 15. Phones and small screens

- **Do:** make the browser window as narrow as a phone (or press F12 and choose a phone in the
  device toolbar) and repeat tests 3–6.
- **See:** everything fits, nothing scrolls sideways, buttons are easy to tap.
- **Proves:** it works for families on phones.

---

## Part 4 — What this version does not do yet

These need **your decision** (numbers refer to the engineering progress document). The code
deliberately does not guess.

| # | Decision | Effect today |
|---|---|---|
| PD-09 | Prices and a payment provider | Plans exist and limits apply; families can ask for an offered plan and an administrator gives or declines it; no payment is taken. |
| PD-25 | What "archive" means (adopted: no new uses; nothing a child has changes) — please confirm | Archive works this way for content and classes. |
| PD-26 | Closing or archiving a whole school | Not offered; classes can be archived. |
| PD-27 | Whether the platform itself should place more practice when a child has played every step | The child can play any lit book again from the shelf; the engine doesn't add practice by itself. |
| PD-04 | Withdrawing a published version from children who already have it | Published content is never changed; new versions are used for new work. |
| PD-14 / PD-15 | Khmer interface text and Khmer/English lessons, written by native speakers and educators | The app is in English; only the Maths path exists (authors can add more in the Studio). |
| PD-20 | Whether content needs a second person's review before publishing | One administrator can publish. |
| PD-24 | Deleting a family account or a child (adopted in Y3 - please confirm: password-confirmed, a 7-day waiting period you can change, then everything about the child is erased; audit records keep ids only) | Families can export their data and delete a child or their account. Also decide how long backups keep erased data. |
| PD-18 | Privacy notice and terms (legal text) | No such pages yet. |
| PD-02 | Ending a sign-in at once (adopted in Y13 - please confirm; `SESSION_CHECK_ENABLED=false` restores the old behaviour) | Signing out, a password change, signing a device out or a suspension stops an open tab on its next action (within 10 seconds). |
| PD-13, PD-16, PD-23 | Learning Buddy's name; one retry before showing the answer; final landing-page wording | Unnamed buddy; answer shown after a miss; honest interim copy. |

**Also outside this version:** a real server, domain, TLS, email provider and backups (see
[DEPLOYMENT.md](DEPLOYMENT.md)), and testing with real families.

---

## Part 5 — How it was verified

At the latest verification (2026-10-06):

- 345 backend tests (unit and integration against a real PostgreSQL), including security,
  a sweep proving no one outside a family can read or change its children's data, role and
  school boundaries, concurrency, and performance (queries don't grow with a child's history
  or a class's size);
- 124 frontend unit tests, type checks, lint, a production build, and a dependency audit with
  no known vulnerabilities;
- 19 automated browser journey runs — family, recovery, admin, boundaries, plans, school
  (principal, teacher, family, archive), content authoring to a child playing it — on desktop
  and on a phone, with automated WCAG 2.2 AA accessibility checks on every main screen, using
  real emails.

The full record — what was built, what was verified and how, and what remains — is in
[BOOKEYOND-KID-ENGINEERING-PROGRESS.md](BOOKEYOND-KID-ENGINEERING-PROGRESS.md).
