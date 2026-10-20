---
name: Bookeyond Kid
description: Learn Today, Build Tomorrow — a children's learning world set in a Cambodian river garden by day, with a calm, evidence-first window for parents and a precise workspace for schools.
colors:
  tonle-50: "#EFF6F6"
  tonle-100: "#DCEBEC"
  tonle-200: "#BCD8DA"
  tonle-300: "#8DBDC2"
  tonle-400: "#5A9AA2"
  tonle-500: "#357B85"
  tonle-600: "#22636E"
  tonle-700: "#1A515B"
  tonle-800: "#154049"
  tonle-900: "#10323A"
  tonle-950: "#0A2127"
  ink: "#162A2D"
  ink-soft: "#43575A"
  ink-faint: "#556568"
  paper: "#F6F3EC"
  paper-raised: "#FFFFFF"
  paper-sunk: "#ECE7DC"
  child-ground: "#E7F1EC"
  line: "#E3DDD0"
  line-strong: "#CBC3B2"
  light: "#F2A93B"
  light-soft: "#FCEED3"
  light-glow: "#FFD27A"
  light-deep: "#8A4A0B"
  rice: "#8DB45A"
  rice-deep: "#4E6B2A"
  clay: "#C8643B"
  clay-deep: "#8F3F1F"
  lotus: "#E48BA0"
  lotus-deep: "#9C3656"
  palm: "#2E7A5A"
  palm-soft: "#E2F0E6"
  palm-deep: "#1E5A41"
  dusk: "#6D5BA8"
  dusk-deep: "#4A3B86"
  brick: "#C2412F"
  brick-soft: "#FBE8E4"
  brick-deep: "#9E2F20"
  sky: "#3B82C4"
  sky-soft: "#E4F0FA"
  sky-deep: "#1F5C93"
  coral: "#E2674A"
  coral-soft: "#FDE8E1"
  coral-deep: "#A03A24"
  cream: "#FBF6EC"
  cream-deep: "#F4EBDA"
  standing-secure: "#2E7A5A"
  standing-progressing: "#3B82C4"
  standing-practice: "#EBA13A"
  standing-early: "#94BEDF"
  standing-none: "#E3E7DF"
typography:
  display:
    fontFamily: "Alegreya Variable, Kantumruy Pro Variable, Georgia, serif"
    fontSize: "clamp(2.5rem, 1.6rem + 3.6vw, 4.75rem)"
    fontWeight: 700
    lineHeight: 1.04
    letterSpacing: "-0.012em"
    fontFeature: "\"lnum\" 1"
  headline:
    fontFamily: "Alegreya Variable, Kantumruy Pro Variable, Georgia, serif"
    fontSize: "clamp(1.875rem, 1.4rem + 1.9vw, 2.75rem)"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.008em"
  title:
    fontFamily: "Alegreya Variable, Kantumruy Pro Variable, Georgia, serif"
    fontSize: "clamp(1.375rem, 1.2rem + 0.7vw, 1.75rem)"
    fontWeight: 700
    lineHeight: 1.22
  body:
    fontFamily: "Kantumruy Pro Variable, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "Kantumruy Pro Variable, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1.4
rounded:
  tag: "6px"
  control: "10px"
  field: "12px"
  panel: "16px"
  sheet: "22px"
  child-card: "32px"
  pill: "999px"
spacing:
  gutter-phone: "16px"
  gutter: "32px"
  section: "112px"
components:
  button-solid:
    backgroundColor: "{colors.tonle-800}"
    textColor: "{colors.paper-raised}"
    rounded: "{rounded.field}"
    height: "44px"
    padding: "0 20px"
  button-solid-hover:
    backgroundColor: "{colors.tonle-900}"
  button-outline:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    height: "44px"
  child-go:
    backgroundColor: "{colors.light}"
    textColor: "{colors.tonle-950}"
    rounded: "{rounded.pill}"
    height: "64px"
    padding: "0 40px"
  child-chip:
    backgroundColor: "rgba(255,255,255,0.9)"
    textColor: "{colors.tonle-950}"
    rounded: "{rounded.pill}"
    height: "44px"
  field:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    height: "48px"
  status-published:
    backgroundColor: "{colors.palm-soft}"
    textColor: "{colors.palm-deep}"
    rounded: "{rounded.tag}"
  status-draft:
    backgroundColor: "{colors.light-soft}"
    textColor: "{colors.light-deep}"
    rounded: "{rounded.tag}"
  workspace-rail:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink-soft}"
    activeBackground: "{colors.tonle-100}"
    width: "248px"
---

# Design System: Bookeyond Kid

## Overview

**Creative North Star: "River Garden"**

Bookeyond Kid lives in a Cambodian village garden on a clear morning: a stilt house with a clay roof, sugar palms with fan crowns, rice paddies in bands of green, a lotus pond, a sandy path down to the Tonle river, a temple far off in the haze. It is drawn as a storybook, original from scratch, in flat, softly shaded planes. River teal is the structure; the one warm voice is marigold *light*, and it always means something: now, next, done well.

The same garden is seen three ways.
- **Children** are inside it. The home is the whole scene: the learning path is a road of books, and the child's Learning Buddy waits at the current book. Activities move into a quiet field (no house, no path, no pond) so a question has nothing to compete with.
- **Parents** see it as a calm band at the top of each page, with their child's own buddy as a portrait. Below that are evidence-first pages on rice paper.
- **Staff** (teachers, principals, administrators) work beside it. A white rail carries the brand, and precise tables and panels sit on quiet paper. There are no buddies in professional space.

**Key Characteristics:**
- Clearly Cambodian and drawn for this product: stilt houses, sugar palms, paddies, lotus, Khmer numerals beside Arabic, and Kantumruy Pro for text.
- A storybook display serif (Alegreya, lining numerals) over a Cambodian text face.
- A family of five Learning Buddies, authored characters rather than chatbots. A child may choose one, and the choice is saved on the child (`learning_buddy`, V58).
- Evidence is visual and always labelled: line weight shows how much evidence stands behind a skill.
- Adult controls are rounded rectangles. Pills, chips and pressable "keys" belong to the child space.

## Colors

A river-teal scale, rice-paper and cream neutrals, the village's materials, one warm voice - and, since
iteration 2, colours with jobs: each subject has its own accent, each standing its own colour, each
workspace its own mark. Teal is the structure and the adult action; it is no longer every card, chart
and heading.

### Primary
- **Deep River** (#154049, tonle-800): the solid adult button. Hover: tonle-900.
- **River** (#1A515B, tonle-700): primary emphasis. White text on it is 8.8:1.
- **River Link** (#22636E, tonle-600): links and focus rings on paper (6.3:1).
- **Night River** (#10323A, tonle-900): the landing footer, the one dark surface left.

### Secondary
- **Marigold Light** (#F2A93B, light): the child's go button, the current book, open lotuses, "up next". Text on a light fill is tonle-950 or light-deep (#8A4A0B, also the "attention" text colour on paper).

### Tertiary (the village's materials)
- **Rice** (#8DB45A / deep #4E6B2A): paddies, fields.
- **Clay** (#C8643B / deep #8F3F1F): roof tiles, book spines, the child initial seal.
- **Lotus** (#E48BA0 / deep #9C3656): the pond, the logo's bloom.
- **Palm** (#2E7A5A / deep #1E5A41): published, secure, available, saved.
- **Dusk** (#6D5BA8 / deep #4A3B86): an illustration accent only.
- **Brick** (#C2412F / deep #9E2F20): errors and destructive actions only.

### Neutral
- **Ink** (#162A2D), **Ink Soft** (#43575A, 6.9:1 on paper), **Ink Faint** (#556568, 5.3:1 on paper and 4.7:1 on paper-sunk).
- **Paper (Y38):** a warm rice-paper ivory, #F6F3EC (was a celadon #F4F6F1), with warm hairlines (#E3DDD0, strong #CBC3B2). The river teal and the marigold read as fine print on it, not as app chrome. White sheets sit on it with the **card** depth: a hairline, a close shadow and a long soft one.
- **Rice Paper** (#F4F6F1) is the page ground. **Raised** (#FFFFFF) is for sheets and panels. **Sunk** (#E8EEE6) is for wells. **Child Ground** (#E7F1EC) sits behind the scene.
- **Line** (#DCE4DA) divides; **Line Strong** (#C2CEC0) strokes fields.

### Roles (iteration 2)
- **Sky** (#3B82C4 / soft #E4F0FA / deep #1F5C93): the open morning; "progressing"; English; the teaching workspace.
- **Coral** (#E2674A / soft #FDE8E1 / deep #A03A24): the lotus at its brightest; Maths; adding a child. An accent and a soft ground only: ink on a coral fill is 4.1:1, so text on coral uses coral.deep (6.7:1 on white, 5.7:1 on coral.soft).
- **Cream** (#FBF6EC / deep #F4EBDA): the family's ground (ink 13.7:1, ink-faint 5.1:1); cream.deep for tab tracks and bands.
- **Standing** (one palette everywhere - parent, teacher, principal, charts): secure #2E7A5A, progressing #3B82C4, needs practice #EBA13A, too early to tell #94BEDF (drawn hollow), not started #E3E7DF. Checked in OKLab under protan/deutan/tritan simulation: every neighbouring pair at least 8.3 apart (normal vision at least 15.7). The old progressing (tonle-600) sat 9.0 from secure for normal vision - too close.
- **Subjects** (`features/learning/subjectTheme.ts`, by subject code, else steadily by code): Maths coral, Khmer marigold, English sky, the world around us leaf, arts lotus; each with an emblem (`SubjectEmblem`) and always beside its name.
- **Workspaces** (`WorkspaceShell` `accent`): teaching sky, school leaf, platform operations river - the rail's top band, the workspace's name and the current item only.

### Named Rules
**The Colour Has A Job Rule.** A colour on a page means one of: brand (teal), a subject, a standing, a workspace, an action, or the world. If it means none of them, it is not there.

**The Light Means Something Rule.** Marigold appears only where something is current, next or achieved. It is never decoration, never a wash, and never a hover colour in adult space.

**The Never Colour Alone Rule.** Every standing, status or match colour sits beside words or a number (a standing's mark plus its name; a pair colour plus its number).

## Typography

**Display Font:** Alegreya Variable, with lining numerals forced on (`"lnum"`) so a 0 never reads as an o.
**Body Font:** Kantumruy Pro Variable, which sets Latin and Khmer in one family and also carries Khmer headings.

**Character:** Alegreya is a calligraphic book serif, the voice of a storybook, warm without being babyish. Kantumruy Pro keeps English and Khmer at the same colour and rhythm.

### Hierarchy
- **Display** (700, clamp 2.5–4.75rem, 1.04): the landing hero, a child's question prompt.
- **Headline** (700, clamp 1.875–2.75rem, 1.1): page openings (Your family, a child's name, Hi, Dara!).
- **Title** (700, clamp 1.375–1.75rem): workspace page headers and sheet titles.
- **Body** (400, 0.9375–1.0625rem, 1.6): explanations, at most about 70ch.
- **Label** (500, 0.8125rem): captions and table headers, in sentence case.

### Named Rules
**The No Kicker Rule.** No small uppercase eyebrow above a heading. Context goes in a breadcrumb or in the sentence under the heading.

## Layout

- **Family space:** a 1180px container with 32px gutters (16px on phones). Each page opens with a scene band. On a child's overview, detail sits in a main column and insights in a 340px sticky column; on phones, insights follow the current path.
- **Staff pages:** beside a fixed 248px white rail (a white top bar with a menu below `lg`), with a 1180px content column.
- **Landing:** paper sections with garden bands, at an 80–112px vertical rhythm.
- **Child space:** always the scene.
  - The home is full-bleed, with a compact greeting card and the journey list below the stage.
  - Activities float one white card over the quiet field.
  - The top bar is chips. On phones the hold-to-leave control is just its lock icon, and the bar wraps rather than clips.

## Elevation & Depth

Depth is paper laid on paper in morning light. Shadows are soft and tinted with river ink, never grey and never hard offsets in adult space.

### Shadow Vocabulary
- **paper** (`0 1px 1px rgba(16,50,58,.05), 0 12px 32px -18px rgba(16,50,58,.32)`): family sheets.
- **lift** (`0 2px 4px rgba(16,50,58,.08), 0 24px 48px -24px rgba(16,50,58,.40)`): the child's card over the scene.
- **glow** (`0 0 0 1px rgba(242,169,59,.45), 0 0 32px 4px rgba(255,210,122,.55)`): the current thing.
- **key press** (`0 5px 0 #C77A1E` on the go button; a darker tone of each answer tile): collapses to 1px on press.

### Named Rules
**The Paper Not Plastic Rule.** No glassmorphism as decoration. A light blur appears only on chips floating over the scene.

## Shapes

- **Adult radii** step from 6px (tags) through 10px (controls) and 12px (fields and buttons) to 16px (workspace panels) and 22px (family sheets).
- **The child space is rounder:** a 32px activity card, 22px panels, 26px answer tiles, pill buttons and chips.
- **Borders** are 1px hairlines. Colour accents never ride on a thick border edge, except a book's spine.

## Components

### Buttons
- **Shape (Y38):** every adult action is a pill: sm 36px, md 44px, lg 52px. Labels may wrap (no `nowrap`), so long Khmer labels grow in height instead of clipping.
- **Solid:** tonle-900 fill, white text, a lit top edge and a short drop (`shadow-button`).
- **Accent:** the marigold invitation, used once per page where a visitor or family starts something (the landing's "Start as a family").
- **Outline / Ghost:** white with a line-strong ring, or text-only tonle-700.
- **Child "go":** a Marigold pill, 64px, tonle-950 text, with a key-press shadow. **Child "quiet":** a white pill, 56px, tonle-800 text.
- **Focus:** a 2px River Link outline at a 3px offset (global `:focus-visible`), on every surface including the child space and the staff rail. Only `.on-night` surfaces (the landing footer) use Marigold Glow `#FFD27A`.

### Inputs / Fields
- **Style:** white, 48px, 14px radius, 1px line-strong inset ring, a faint inner shadow (a well, not a box). Hover: tonle-300. Focus: 2px tonle-600 with a soft 4px halo.
- **Error:** a 2px brick ring, with the message in brick-deep beside an icon, linked by aria-describedby.

### Cards / Containers
- **Family sheet:** white, 22px, paper shadow, line/70 ring. Rows divide with 1px lines instead of nesting cards.
- **Workspace panel:** white, 16px, 1px line ring, with a header row and a hairline under it. Tables run edge to edge (`flush`) and become stacked lists on phones. A class roster of more than 8 children reads in two columns from `xl`, top to bottom and then the next, with a hairline between.
- **Composer:** a creation form opens from a "New …" action in its list header as a titled, closable panel (Escape closes it too). The first field takes focus.

### Page frame
- Every page names the browser tab "Page · Bookeyond Kid".
- Family, staff and landing pages start with a "Skip to content" link to `#main`.

### States
- **Loading:** three small books breathing, which fade in only after a noticeable wait and only before the first data arrives.
- **Empty:** a dashed outline, a title, and one sentence on what to do next. Family empty states may show the buddy.
- **Error:** "That didn't load", the reason, and "Try again". The child space uses the buddy (encourage mood) and a pressable retry.
- **Limits and meters (Y35):** a plan or storage limit is shown before anyone reaches it. Each meter is a thin bar (`role="meter"`, with its value as text beside it) in tonle-600. From 80% it turns to the warm *light*, and the words say "Nearly full", "Full: new ones wait" or "Over: everything stays". It is never brick-red, because a limit never takes anything away. A refusal shows the server's sentence next to the action that caused it. Limits apply only to adults' actions, so the child space never shows one.

### Navigation
- **Family:** a white sticky bar with text links and a 2px underline indicator, on a cream ground. A child's page has sections as link tabs on a cream track: Overview (learning plan, what changed, insights), Skills & progress (skills, recent activity), School & settings (class codes, the child's own space, deletion).
- **Staff (Y38):** a deep-river rail (tonle-950) with the paper lockup and a 3px band in the workspace's accent (teaching sky, school leaf, operations marigold). Nav text is tonle-100 and labels tonle-300 (>= 8:1). The active item is a 9% white ground with a 3px inset bar and icon in the accent. The phone bar is the same night. Focus on the rail is the night ring, Marigold Glow. Work sits on warm paper in white **card** sheets with 20px radius. Page titles are Alegreya 2.5rem bold. Sub-pages carry a slash breadcrumb ("Classes / Grade 1 A").
- **Brand lockup (Y41):** the official Bookeyond badge (supplied by the owner; PD-34 resolved), with **KID** centred underneath in Alegreya 700 spaced capitals, about a third of the badge's width. Bookeyond leads; Kid is the product label, never a badge beside it. Its blue is the platform's deep river (tonle-800), as the owner allowed; nothing else is changed. Source, colourings and the asset script: `brand/official/`. The single slot: `components/brand/officialMark.ts`.
- **Landing:** a light paper bar with a Deep River "Get started" button, and a deep-teal footer.
- **Principal (Y37):** one job per page. **Overview** has the glance, learning across the school and a one-line plan summary. **Classes**, **Teachers** and **Plan** are each their own address in the rail. The other pages show the school's name above their own heading. With more than one school, a picker is shown, and the chosen school is remembered across the pages.
- **Teacher home (Y37):** classes first. Then "Your content": their content, and their pictures and recordings with the storage bar. Then their schools.

### Signature Components
- **Activity builder (Y42)** (`features/admin/studio/ActivityBuilder`): a draft activity is one place to write a quiz, in three numbered steps. **1 Write the questions**: numbered cards (type picture, prompt, "New" or "Published"), edited in place, reordered or taken out with immediate saves; **Add a question** opens "What should children do?" - seven cards, each a picture, a plain name, what the child does and an example - and the form for that type, tagged with the activity's skills. Reusing a published question and the extra settings are tucked into disclosures. **2 Ready to publish?**: every check publishing will run, grouped by the activity and by question. **3 Publish**: one button that names how many new questions go with it, a plain-words confirmation, and an all-or-nothing publish. Where review is required, the review flow stays. After publishing: "Get it to children" says the real next step for a teacher or an admin. **New activity** is one form (name, what it practises, lesson or a new one) that lands in the builder.
- **Hint and explanation (Y41):** educator-written, part of the question's version. The hint waits behind a "Need a hint?" pill (lightbulb) under the interaction, closed until the child asks, and goes once they answer; a hint that says a typed answer can't be published, one naming the right choice is flagged. The explanation comes only with the answer, right or wrong: a white card headed "Why" under the verdict and the right answer. Both stay in the curriculum's own language. The Studio form says when the child sees each; the preview shows the hint, and the explanation after a try.
- **Garden in the world (Y40)** (`features/world/WorldGarden`): the child's garden is also a bed of turned earth in the River Garden, at the stages the answers support (up to eight plants, blooms in front). The cards cover different parts of the scene on different screens, so the bed has two places: the field left of the path on a phone or tablet (`spot="field"`, `lg:hidden`), the near ground right of it on a large screen (`spot="near"`). A child with no answers sees the bed freshly turned. Drawn on the home and on each journey.
- **Verdict as a picture (Y40):** the answer bar leads with the world's own sign - an open lotus for right, a bud for "not quite" - so a child who can't read the words still knows. A wrong typed answer gets the right one beside it in the same size (`7 → 3`); a wrong order gets "The right order" laid out as tiles. Both are decorative; the words say it to a screen reader.
- **Whole-basket sorting (Y40):** a picked tile drops into a basket wherever the child taps it, not only on its name; the name stays the keyboard and screen-reader control.
- **Next lantern (Y40):** beside the lit lanterns, one swaying unlit lantern per path for what it is working towards (the first book, its current chapter all lit, or the whole path) with "2 more books". From path state only - never a quota or a deadline.
- **What's in this book (Y40):** the activity intro shows the kinds of question it holds - choose, true or false, numbers, write, put in order, match, sort - as a drawn picture with one word each.
- **Family overview (Y40):** each child is four ruled cells, not a stack of panels: **Learning** (subjects with steps done, where they are, the last activity), **What the answers show** (the standing bar), **The last 4 weeks** (what became secure or moved up, what may need practice - each linked to its evidence - and "What changed"), **Next** (the path's step with "Ready when X is", or, when a path is done or missing, the parent's decision as a marigold action). How to read the figures is said once, in "About these numbers".
- **Garden of skills (Y39)** (`features/child/{garden,SkillGarden}`, `features/world/Plant`): the child's one collectible. Each practised skill is a plant whose stage is read from the server's standing: **bloom** (secure, "I can do this"), **bud** (progressing, "Getting stronger"), **sprout** (any other skill with answers, "Practising" since Y41). A weak skill is never labelled as weak to the child. Plants share one ground line on a pale-green bed; the shelf shows the bed, the entrance a pill glimpse ("Visit my garden · 1 bloom · 2 sprouts"). After an activity (Y41), by the server's own change rule (`ProgressChangeService.kind`, mirrored in `garden.ts`): **Your garden grew!** only when a standing became secure (mastery, a bloom) or moved up (a bud) - the same two moves the parent sees as good news; a skill's first answers are "New in your garden" (planted, or a new bud when its first reading is getting stronger); anything else is "Good practice!", warm words for the effort. A slip is never told to the child. There are no points, coins, streaks or rankings.
- **How to answer (Y39)** (`features/child/HowTo`): ordering, matching and sorting questions open with a pill that pairs the instruction with a drawn gesture (ordered list, link, drop into a basket), so a child who can't read the sentence yet still sees what to do. The words stay the accessible text.
- **Parent signals (Y39)** (`features/parent/signals.messages`): under each child's head, a two-line `<dl>`: **Recently** (one activity's answers, e.g. "2 of 3 answers right, today", with the note that this is not a judgement of the skill) and **Next** (named as a review when the server says it is one). Each line shows only when there is data behind it. Path counts say "X of Y steps done", never "progress".
- **On this page (Y39)** (`features/school/SectionIndex`): on a class page (teacher and principal), a wrap of pill links built from the section headings actually on the page, so it is in the page's language and never lists a section this class doesn't show. It moves focus to the heading it jumps to, and isn't shown for fewer than three sections.
- **Child entrance (Y38):** the world above, a greeting band (the name, one line and "keep going"), then each subject as a **destination**. It has an illustrated header in the subject's soft colour with its emblem standing on a pale hill. Below that: the name in Alegreya, where the child is, lotus progress (open lotuses for steps done, a taller bud for the next) and a marigold "Next: <activity>" ribbon. One per row on a phone, up to three on a desktop.
- **Activity page (Y38):** a storybook sheet with a printed frame line 10px inside its edge. A perfect activity or a finished chapter throws 18 motes of marigold and lotus up from the page and fades them (1.1s). This is decorative, hidden from assistive tech and absent under reduced motion. The words never claim mastery.
- **Landing: "Watch understanding grow" (Y39):** after the buddy world, the garden told to a parent: the three plant stages drawn by the real component, three true points (grown only from the child's answers; no points, coins or streaks; the same evidence the parent sees) and a labelled product screenshot.
- **Landing order (Y37):**
  - hero, then families and schools as one ruled band (no pastel cards);
  - how it works; evidence; the buddy world;
  - **Safe by design. You decide.**: six things the product really does, as a ruled two-column list with drawn icons;
  - subjects;
  - **Plans**: early access for families, "Nothing to pay"; schools "Arranged with us". No invented price, and nothing is promised about paid plans;
  - schools; the closing band.
  - The navigation is How it works, For parents, Safety, Plans, Schools.
- **Break suggestion (Y36):** on the activity-complete page only, a palm-soft panel with the buddy (happy). **Take a break** is the primary action and **One more** the quiet one. There is no countdown or timer graphic.
- **Child setup** (`/parent/add-child`): three steps - about, subjects (one card per subject from the server's catalog for the child's age, with its emblem), review. Resumable (`?child=`).
- **Subject door** (the child's entrance): a large card per subject the family chose - emblem, name, what's next, books done in the subject's colour - opening that subject's journey.
- **Child summary** (family home): a cream header with the child's buddy portrait and actions; subjects with progress in their colour; a standing bar; skills flagged for practice.
- **River Garden** (`features/world/RiverGarden.tsx`): the scene, in three variants.
  - `home`: house, path, pond and temple.
  - `band`: a calm strip for parent pages and the landing.
  - `quiet`: sky, hills and fields only, behind activities.
  - `bloom` (0–1) opens the pond's five lotuses as the child's chapters finish.
  - Art direction (iteration 2): one low sun at the upper left; volumes lighter on the left, shadows to the right; the air thickens with distance (far ridges and the temple pale and blue, colour warming and sharpening towards the front); each layer a little more detailed than the one behind; the world has life in it (egrets, a buffalo, a boat, a krama drying, a spirit house) but never a crowd of symbols.
- **Path of Books:** the child's path as books along the village path. Lit means done, glowing means current (the buddy stands there), plain means later. Each is labelled "៥ · 5".
- **Progress Buds:** an activity's questions as a row of buds that open as they are answered. They replace a percentage bar.
- **Evidence Line:** evidence strength as line weight, with a text label always beside it.
  - Dotted hairline: none.
  - Dashed: partial.
  - 3.5px: sufficient.
  - 5.5px: strong.
- **Learning Buddies** (`features/buddy`): Momo the otter, Domrey the elephant, Krobey the water buffalo, Toch the gibbon and Kongkep the frog.
  - They share one pose system (arms, eyes, mouth, cheeks) with the moods idle, happy, thinking, cheer and encourage.
  - Rendering (iteration 2): one key light from the upper left; gradient volumes, the head's shadow on the body and a rim of light on the far edge; limbs are shaped tubes ending in paws, hands, hooves or webbed feet (never single strokes); a few strokes of texture; no outlines.
  - Authored, never conversational.
  - The child chooses one on `/learn/:childId/buddy`; parents see it as the child's portrait.
  - The names are proposals pending product review (PD-13).
- **Village Places** (`features/marketing/Places.tsx`): subjects as places on the landing. A bridge is Maths, a pavilion Khmer, a treehouse English. Only Maths is marked ready.
- **Brand mark (Y41):** the official Bookeyond badge alone (`BrandMark`) where space is tight - the child's space, phone headers, the staff rail on a phone - with a fine light ring on dark grounds. The app icons (`frontend/public`) are the badge on warm paper; `brand/official/make_assets.py` rebuilds them. The Y33–Y38 drafted product mark is retired.

## Do's and Don'ts

### Do:
- **Do** open family pages with the garden band and keep the child's home inside the full scene.
- **Do** keep activities on the quiet field: one task, obvious answers, one next action.
- **Do** state evidence plainly beside its drawing ("4 of 6 answers correct", "Enough answers to judge").
- **Do** keep Khmer beside English where it carries identity (numerals, subject names).
- **Do** use `motion-safe:` for every animation. Reduced motion shows the final state.
- **Do** make hover styles hover-only (`hoverOnlyWhenSupported`).

### Don't:
- **Don't** add leaderboards, streak pressure, points, currencies or confetti. Celebration is a lotus opening, a lit book, the buddy cheering.
- **Don't** use marigold for anything that isn't current, next or achieved.
- **Don't** put buddies or scenery inside staff workspaces.
- **Don't** put uppercase eyebrow labels over headings, gradient text, hero-metric tiles or same-size icon-card grids.
- **Don't** present completion as mastery. Steps completed and skills evidenced are shown separately.
- **Don't** set text in tonle-500 or lighter on light grounds; links and quiet teal text use tonle-600.
