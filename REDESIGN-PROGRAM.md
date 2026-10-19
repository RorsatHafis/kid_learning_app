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
| 6 | Landing page and marketing story | ⏳ In progress |
| 7 | Teacher, principal and admin workspaces | ⬜ |
| 8 | Bilingual, responsive and shared components | ⬜ |
| 9 | Bounded final review and commit | ⬜ |

## Decisions

- **Logo:** the genuine Bookeyond logo is still unavailable (PD-34). The replaceable slot is
  `frontend/src/components/brand/officialMark.ts`.
- **Hints and explanations:** none exist in the content model, so none are shown. Recorded as a
  content gap.
- **Garden of skills:** the child's only collectible, grown solely from server standings.
- **"Challenge Garden" and "Discover":** not product capabilities and not invented.

## Next step

Step 6: landing - the garden in the story, fresh product shots.
