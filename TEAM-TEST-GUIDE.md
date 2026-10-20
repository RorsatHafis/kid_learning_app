# Team test guide — 2026-10-20

What changed since the last round (Y39–Y42), what to try in each role, and how to report. Every item
below is a real feature; nothing here is a mock-up.

## Before you start: update the test server

The new version adds one database migration (V61: an optional hint and explanation on each question).
It runs by itself when the backend starts. On the machine that runs the team's Docker stack:

```bash
scripts/backup.sh ./backups
git pull
docker compose up -d --build
```

Then sign in as usual. Nothing is deleted or changed in existing families, children or content.

## Teacher or admin: make a quiz (the new builder)

1. Content Studio → a subject → **Activities** → **New activity**.
2. Give it a name children will see, tick what it practises, pick a lesson (or a new one) →
   **Create and write questions**.
3. **Add the first question** → choose what children should do (the cards show an example of each):
   choose the answer, true or false, type a number, type a word, match pairs, put in order, sort into groups.
4. Write the question. Optional: a **Hint** (shown only if the child asks, before answering - it must not
   give the answer) and an **Explanation** (shown after the answer, right or wrong).
5. Add more questions; reorder or remove them with the buttons on each card. Changes save at once.
6. **Ready to publish?** lists anything to fix, question by question. When it says *Everything is
   ready*, press **Publish activity and N new questions** and confirm. All of it is published, or -
   if anything is wrong - none of it.
7. Teachers: open **Your classes** → the class → a skill the class learns that the activity practises →
   assign it as practice.
   Admins: put it on a path through **Curricula**.

Try to break it: a hint that says the answer ("It is 7"), a number question without its answer, an
empty activity, two teachers editing each other's activity, publishing twice.

## Child (sign in as a parent, then "Start … session")

- A question with a hint shows **Need a hint?** - closed until tapped, gone once answered.
- After answering: the verdict as a lotus picture, the right answer beside a wrong one, and - when the
  author wrote one - a **Why** card.
- At the end: *Your garden grew!* only when a skill really got stronger; *New in your garden* for a
  skill's first answers; *Good practice!* otherwise. The garden also grows in the world beside the path.
- Switch the child's language (the ខ្មែរ button) and play again: the interface is Khmer, the lesson
  content stays in its own language.

## Parent

- Family home: each child shows **Learning**, **What the answers show**, **The last 4 weeks** and
  **Next**. "Choose what comes next" appears only when a decision is yours.

## Everyone

- The official Bookeyond logo with **KID** underneath, on every page and as the browser icon.
- Try a phone and a computer, English and Khmer.

## How to report

For each problem: role, page (copy the address), phone or computer, language, what you did, what you
expected, what happened, and a screenshot. Mark it **blocker** (can't continue), **wrong** (incorrect
result or text) or **polish** (works, could be better).

## Known limits (not bugs)

- Hints and explanations appear only where an educator has written them; none are generated.
- The Khmer interface text is a draft awaiting native review (PD-14).
- Where content review is switched on, publishing goes through a reviewer, so the one-step publish
  isn't offered.
