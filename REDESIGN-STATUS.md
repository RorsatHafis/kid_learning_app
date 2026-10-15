# River Garden redesign — working status

Direction contract: `.impeccable/surfaces/frontend-src-app-tsx.md`. Design system: `DESIGN.md` (rewritten
for River Garden). Replaces "Blue hour on the Mekong". Progress record: Y33 in
`BOOKEYOND-KID-ENGINEERING-PROGRESS.md`.
Reference: owner's Figma "Bookeyond Kid · River Garden" (frame 03: Momo the otter, cocoa coat, cream
muzzle and belly, blue lotus neckerchief, eight poses). Its houses and trees were not kept.

## Phases

- [x] A. Foundation: palette tokens, focus/selection, workspace rail (light), buttons, fields, states
- [x] A. Buddy family (Momo otter + elephant, buffalo, gibbon, frog) with moods; child's choice saved (V58)
- [x] A. River Garden scene (replaces BlueHour), path of books in daylight, progress buds
- [x] B. Child: home, buddy chooser, shelf, play intro, questions, feedback, completion
- [x] B. Landing, auth pages (legal content renders inline through shared components)
- [x] B. Parent: home (buddy portrait, skill tally), child overview, skill page, account
- [x] B. Teacher, principal, admin: shell restyled (light rail); every page reviewed, no page-level changes needed
- [x] C. Browser review desktop (1440) + phone (390), every role; child header fixed for phones
- [x] D. Vitest, tsc, lint, build, E2E (see Y33 for the exact results)
- [x] E. DESIGN.md rewritten, progress doc row Y33

## Still open (deliberately not done here)

- PD-13: the buddy names (Momo, Domrey, Krobey, Toch, Kongkep) and characters are proposals; they need
  product sign-off.
- PD-14: the Khmer for the new copy (buddy names and lines, landing, parent tally) is Claude-drafted, like
  the rest of Y32; it needs native review.
- Print artwork in `brand/print` predates the River Garden colours (teal book, lotus-pink bloom) and should
  be regenerated with `brand/tools`.
- The main JS chunk is about 585 kB (the warning existed before; the redesign added about 19 kB). Route-level
  splitting is still to do.
- Pages not reachable with the seeded data (for example a teacher's class with many children and several
  weeks of answers) were reviewed only as far as the isolated data allowed.
