# Redesign program (product design transformation)

Started 2026-10-19 from commit 868f7c2. Each step is marked when done, so it is never repeated.
Direction and research: DESIGN-DIRECTION-Y39.md. Engineering detail: the Y39+ rows in
BOOKEYOND-KID-ENGINEERING-PROGRESS.md.

| # | Step | Status |
|---|---|---|
| 1 | Inspect the current state; finish any active task | ✅ Done: clean tree at 868f7c2, nothing active |
| 2 | Research and short design rationale | ✅ Done: DESIGN-DIRECTION-Y39.md |
| 3 | Shared visual system | ✅ Done in Y38 (warm paper, pills, card depth, lockup, night rail); extended per step below |
| 4 | Child experience and meaningful gamification | ✅ Done: garden of skills (shelf + home glimpse), "your garden grew" on completion from real standings, review chip, chapter trail, interaction pictograms, finished-path replay hint |
| 5 | Parent dashboard, statistics, drill-downs | ✅ Done: per-child "Recently" (one activity's answers, labelled as such) and "Next" (named as a review when it is one) signals shown only with data; no duplicate date; path count says "steps done"; practice box only when flagged (Y37); drill-down Overview → child → Skills & progress → skill evidence kept |
| 6 | Landing page and marketing story | ✅ Done: "Watch understanding grow" section (real plant stages, real garden shot with sample data, no points/coins/streaks), fresh child-entrance and parent shots matching the current product |
| 7 | Teacher, principal and admin workspaces | ✅ Done: teacher home in two columns (classes are the work; content, media and schools beside them); "On this page" index on the long class page for teachers and principals, built from the headings actually shown; Studio subject list drops the placeholder dash. Principal home (reach, setup gaps, learning across classes, plan) and admin dashboard (what waits on you first, platform numbers beside) were already summary-first and stay |
| 8 | Bilingual, responsive and shared components | ✅ Done: Khmer captured at phone width with the account and the child each switched to Khmer (entrance, shelf and garden, parent home, child page, account, sign-in) and restored after; tablet (768) captures of landing, parent, child and teacher; desktop and phone of every changed screen. No defect needed a code change; curriculum titles correctly stay in their own language |
| 9 | Bounded final review and commit | ✅ Done: tsc, ESLint (0 errors), Vitest 50 files / 275 tests, vite build; E2E accessibility 8, golden 9, school 2, studio 4, language 4, plan 4, navigation 4 (all passed); Y39 row in the progress record; DESIGN.md components |

## Decisions

- **Logo:** the genuine Bookeyond logo is still unavailable (PD-34). The replaceable slot is
  `frontend/src/components/brand/officialMark.ts`.
- **Hints and explanations:** none exist in the content model, so none are shown. Recorded as a
  content gap.
- **Garden of skills:** the child's only collectible, grown solely from server standings.
- **"Challenge Garden" and "Discover":** not product capabilities and not invented.
- **Teacher authoring readiness:** no per-teacher summary of drafts or review outcomes exists in the
  API, so the teacher home doesn't invent one; reviews stay visible in the Studio itself.

## Next step

The program is complete. What remains waits on the owner: the official Bookeyond logo (PD-34), native Khmer review (PD-14), and educator-authored curriculum (PD-15).

---

# Deep UX redesign (Y40)

Started 2026-10-19 from aed3a32. Phases are marked when done, so they are never repeated.

| # | Phase | Status |
|---|---|---|
| 1 | Child experience | ✅ Garden in the world (by the path), verdicts as lotus pictures, right answer and right order shown in place, whole basket takes a tile, next lantern with books to go, intro shows what the book asks (pictures + one word) |
| 2 | Parent dashboard and statistics | ✅ Each child answers four questions in four ruled cells: Learning (subjects, where, last activity), What the answers show (standing bar), The last 4 weeks (what became secure or moved up, what may need practice, link to What changed), Next (the path's step, or the parent's decision when a path is done or missing). Removed: duplicated "every step done", per-child empty "nothing flagged" panel, per-child notes (now one "About these numbers") |
| 3 | Teacher, principal, admin deeper screens | ✅ Principal Teachers page says which classes each teacher teaches (or that they have none yet), linked; Studio activities as one ruled sheet of lessons instead of a card each. Reviewed and kept: Studio authoring (checks, preview, review, versions, skills), class workspace, admin accounts, schools, plans, storage, learning operations |
| 4 | Landing and design-system gaps | ✅ Hero screenshots re-captured from the redesigned product (parent four-cell overview; child home with the garden by the path) and their descriptions updated; the rest of the landing kept as completed in Y39 |
| 5 | Responsive and bilingual pass; final checks | ✅ Khmer on a phone: child home, shelf, a matching activity through completion, parent overview (account and child switched to Khmer, then restored); desktop, tablet and phone of the changed screens; tsc, ESLint, Vitest 51/280, build; E2E accessibility 8, golden 9, school 2, studio 4, language 4 |

**Child review (what was only restyled, what was weak):** the world showed chapters (pond lotuses) but
not what the child can do; a wrong typed answer or order was corrected only in words; the sorting basket
took a tap only on its name; the lantern rail showed only past lanterns, nothing to look forward to.
Hints, missions, points and new destinations have no backing in the content model or the API, so they
are not invented.

**Y40 complete.** What remains waits on the owner: the official Bookeyond logo (PD-34), native Khmer review
of the drafted copy (PD-14), educator-authored curriculum (PD-15), and hints or explanations in the content
model if they are wanted.

---

# Learning feedback and official brand (Y41)

| # | Item | Status |
|---|---|---|
| 1 | Practice, improvement and mastery told apart | ✅ The completion screen uses the server's change rule; "grew" only for became secure or moved up; first answers are "new"; the rest is "good practice"; sprouts read "Practising" |
| 2 | Author-written hints and explanations | ✅ V61; Studio authoring, checks and preview; hint before answering (never the answer), explanation only with it |
| 3 | Child-friendly feedback | ✅ "Need a hint?" pill; "Why" card under the verdict and the right answer; Khmer and English checked |
| 4 | Official Bookeyond logo | ✅ The supplied badge, recoloured to the platform teal as allowed, with KID underneath at every touchpoint; drafted mark retired |

Hints and explanations exist only where educators write them (PD-15); the interface Khmer still needs native review (PD-14).

---

# Simpler authoring for staff (Y42)

| # | Item | Status |
|---|---|---|
| 1 | New activity in one step | ✅ Name, what it practises, lesson (or a new one), then straight into the builder |
| 2 | Write questions inside the activity | ✅ Friendly type cards with examples; the question takes the activity's subject and skills; one transaction creates and adds it |
| 3 | One safe publish | ✅ Combined checks by question; plain confirmation; the activity and its new questions publish together or not at all; review gate unchanged |
| 4 | After publishing | ✅ "Get it to children": teachers assign from a class's skill, admins use curricula |
| 5 | Team test guide | ✅ TEAM-TEST-GUIDE.md |

Rule change, deliberate: an activity **draft** may now hold draft questions (superseded versions are still refused); an activity is still published only when every question in it is, and children are only ever served published content.
