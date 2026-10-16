# River Garden redesign — working status

Direction contract: `.impeccable/surfaces/frontend-src-app-tsx.md`. Design system: `DESIGN.md` (rewritten
for River Garden). Replaces "Blue hour on the Mekong". Progress record: Y33, Y34 and Y35 in
`BOOKEYOND-KID-ENGINEERING-PROGRESS.md`.
Reference: owner's Figma "Bookeyond Kid · River Garden" (frame 03: Momo the otter, cocoa coat, cream
muzzle and belly, blue lotus neckerchief, eight poses). Its houses and trees were not kept.

## Phases (Y33)

- [x] A. Foundation: palette tokens, focus/selection, workspace rail (light), buttons, fields, states
- [x] A. Buddy family (Momo otter + elephant, buffalo, gibbon, frog) with moods; child's choice saved (V58)
- [x] A. River Garden scene (replaces BlueHour), path of books in daylight, progress buds
- [x] B. Child: home, buddy chooser, shelf, play intro, questions, feedback, completion
- [x] B. Landing, auth pages (legal content renders inline through shared components)
- [x] B. Parent: home (buddy portrait, skill tally), child overview, skill page, account
- [x] B. Teacher, principal, admin: shell restyled (light rail); every page reviewed
- [x] C. Browser review desktop (1440) + phone (390), every role; child header fixed for phones
- [x] D. Vitest, tsc, lint, build, E2E
- [x] E. DESIGN.md rewritten, progress doc row Y33

## Closing the gaps (Y34)

- [x] Khmer: every string the redesign added or changed is in KHMER-REVIEW-Y33.md for one native
      review pass; a stale "firefly" promise fixed in both languages; Khmer screens checked in the
      browser at 1440, 390 and 320 px (no broken glyphs, no clipping, no sideways scrolling).
- [x] Print artwork and app icons rebuilt from the live logo (`brand/tools/build.mjs`): Alegreya
      outlines, river-teal and lotus colours, vector PDFs without fonts or background; favicon,
      home-screen icons and theme colour no longer navy.
- [x] Large data: a 35-child class with seven weeks of answers, an empty class and a long-named class,
      reviewed for teacher and principal at three widths; summaries reconciled with the data; the
      roster reads in two columns on wide screens.
- [x] Skipped E2E: legal (2) and review (2) pass when their features are configured; they skip on
      the default configuration by design (PD-18 documents, PD-20 switch).
- [x] Bundle: family pages and the child's space are separate downloads; main chunk 585 → 339 kB.
- [x] Buddy names: one source (the catalog), used consistently; still proposals (below).

## Design iteration 2 (Y35)

What was wrong, and what changed:

- **Colour had no jobs.** One teal did everything, and "progressing" (tonle-600) sat 9.0 from "secure"
  in OKLab for normal vision, so class charts blurred them. Now: sky, coral and cream join the palette;
  one standing palette is used everywhere (checked under colour-blindness simulation); each subject has
  an accent and an emblem; each staff workspace has its own mark in the rail.
- **The art was stickers.** Buddies had single-stroke arms and one body shape; the scene was flat colour
  fields. Buddies are rebuilt with shaped limbs and paws, form shading and texture; the scene has
  atmospheric depth, lit vegetation, a waterway, wildlife and a lived-in house. Hand-built vector art -
  no image-generation tool was available in this environment.
- **Children were dropped straight into Maths.** Child creation auto-started the single curriculum for
  the age (Battle 2B). Now a family chooses subjects as it adds a child (`/parent/add-child`, backend
  `familyChoosesSubjects`), the choice is the child's real enrolments, and it is changed later from the
  child's learning plan.
- **The child's home had no entrance.** It now opens on the child's subjects; each opens its journey.
  Activities are unchanged and stay simple.
- **The parent space was thin.** The family home shows each child at a glance (subjects with progress, a
  standing bar, skills flagged for practice, next actions, the family's actions); the child page has
  sections (overview, skills & progress, school & settings).
- **The landing page was an illustration.** It now speaks to families and schools separately, shows the
  real product (screens captured from the app with sample data, labelled as such), lists only the
  school capabilities that exist, and the school call to action opens registration as a principal.

Open from this iteration: the new Khmer (107 strings, in KHMER-REVIEW-Y33.md) awaits native review;
the landing screenshots must be re-captured when the screens change (`frontend/scripts/capture-marketing-shots.mjs`
on an isolated stack).

## Learning Buddy names — awaiting the owner (PD-13)

Proposed: Momo (otter), Domrey (elephant), Krobey (water buffalo), Toch (gibbon), Kongkep (frog).
They live only in `frontend/src/features/buddy/buddy.messages.ts`; the server stores the species
(`OTTER`, `ELEPHANT`, …), so renaming later is one catalog change with no migration.

What the review found: only Momo is a name. The other four English names are the Khmer words for
the animal, so a Khmer-speaking child reads "Elephant", "Buffalo", "Gibbon", "Frog" — while an
English reader sees names. The set follows two rules at once.

Recommendation: one rule for all five, chosen for the children who will use the product first
(Khmer-speaking, 4–9):

1. **Preferred:** give the four a short personal name each, like Momo — one or two syllables, easy
   for a four-year-old to say, distinct first sounds, not a common child's name (a buddy shouldn't
   share a name with a classmate) — written once and used in both languages. The names should come
   from, or be checked by, the native-speaking reviewer (PD-14), not from Claude.
2. **Acceptable:** keep the species words as the buddies' names and give the otter its species word
   too, so all five follow the same rule; simplest, and it teaches the animal words.

Until decided, the current names stay in the product, marked as proposals in the catalog,
DESIGN.md and here.

## Still open (deliberately not done here)

- PD-13: the buddy names and characters (above).
- PD-14: the redesign's Khmer is Claude-drafted; KHMER-REVIEW-Y33.md is ready for the reviewer.
- Print colours are sRGB only; a CMYK/Pantone proof (lotus pinks and marigold first) is a printer's job.
- Both languages still ship to every visitor (Khmer letters are 33 kB of the 339 kB main chunk, before gzip);
  loading the other language on demand is possible later.
- The buddy chooser's radios take a Tab stop each (Enter/Space choose); arrow keys don't move
  between them, because choosing saves at once.
