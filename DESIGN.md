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
  ink: "#172B2E"
  ink-soft: "#46595B"
  ink-faint: "#5B6C6E"
  paper: "#F4F6F1"
  paper-raised: "#FFFFFF"
  paper-sunk: "#E8EEE6"
  child-ground: "#E7F1EC"
  line: "#DCE4DA"
  line-strong: "#C2CEC0"
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

A river-teal scale, rice-paper neutrals, the village's materials, and one warm voice.

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
- **Ink** (#172B2E), **Ink Soft** (#46595B, 6.8:1 on paper), **Ink Faint** (#5B6C6E, 5.1:1 on paper and at least 4.7:1 on every light ground, paper-sunk included).
- **Rice Paper** (#F4F6F1) is the page ground. **Raised** (#FFFFFF) is for sheets and panels. **Sunk** (#E8EEE6) is for wells. **Child Ground** (#E7F1EC) sits behind the scene.
- **Line** (#DCE4DA) divides; **Line Strong** (#C2CEC0) strokes fields.

### Named Rules
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
- **Shape:** an adult rounded rectangle (12px; sm 10px, lg 14px).
- **Solid:** Deep River fill, white text, 44px tall.
- **Outline / Ghost:** white with a line-strong ring, or text-only tonle-700.
- **Child "go":** a Marigold pill, 64px, tonle-950 text, with a key-press shadow. **Child "quiet":** a white pill, 56px, tonle-800 text.
- **Focus:** a 2px River Link outline at a 3px offset (global `:focus-visible`), on every surface including the child space and the staff rail. Only `.on-night` surfaces (the landing footer) use Marigold Glow `#FFD27A`.

### Inputs / Fields
- **Style:** white, 48px, 12px radius, 1px line-strong inset ring. Hover: tonle-300. Focus: 2px tonle-600.
- **Error:** a 2px brick ring, with the message in brick-deep beside an icon, linked by aria-describedby.

### Cards / Containers
- **Family sheet:** white, 22px, paper shadow, line/70 ring. Rows divide with 1px lines instead of nesting cards.
- **Workspace panel:** white, 16px, 1px line ring, with a header row and a hairline under it. Tables run edge to edge (`flush`) and become stacked lists on phones.
- **Composer:** a creation form opens from a "New …" action in its list header as a titled, closable panel (Escape closes it too). The first field takes focus.

### Page frame
- Every page names the browser tab "Page · Bookeyond Kid".
- Family, staff and landing pages start with a "Skip to content" link to `#main`.

### States
- **Loading:** three small books breathing, which fade in only after a noticeable wait and only before the first data arrives.
- **Empty:** a dashed outline, a title, and one sentence on what to do next. Family empty states may show the buddy.
- **Error:** "That didn't load", the reason, and "Try again". The child space uses the buddy (encourage mood) and a pressable retry.

### Navigation
- **Family:** a white sticky bar with text links and a 2px underline indicator.
- **Staff:** a white rail with the Logo, the workspace name, sentence-case section labels and nav items (10px radius; the active item is tonle-100 with tonle-950 text). Account and log out sit at the foot. Sub-pages carry a slash breadcrumb ("Classes / Grade 1 A").
- **Landing:** a light paper bar with a Deep River "Get started" button, and a deep-teal footer.

### Signature Components
- **River Garden** (`features/world/RiverGarden.tsx`): the scene, in three variants.
  - `home`: house, path, pond and temple.
  - `band`: a calm strip for parent pages and the landing.
  - `quiet`: sky, hills and fields only, behind activities.
  - `bloom` (0–1) opens the pond's five lotuses as the child's chapters finish.
- **Path of Books:** the child's path as books along the village path. Lit means done, glowing means current (the buddy stands there), plain means later. Each is labelled "៥ · 5".
- **Progress Buds:** an activity's questions as a row of buds that open as they are answered. They replace a percentage bar.
- **Evidence Line:** evidence strength as line weight, with a text label always beside it.
  - Dotted hairline: none.
  - Dashed: partial.
  - 3.5px: sufficient.
  - 5.5px: strong.
- **Learning Buddies** (`features/buddy`): Momo the otter, Domrey the elephant, Krobey the water buffalo, Toch the gibbon and Kongkep the frog.
  - They share one pose system (arms, eyes, mouth, cheeks) with the moods idle, happy, thinking, cheer and encourage.
  - Authored, never conversational.
  - The child chooses one on `/learn/:childId/buddy`; parents see it as the child's portrait.
  - The names are proposals pending product review (PD-13).
- **Village Places** (`features/marketing/Places.tsx`): subjects as places on the landing. A bridge is Maths, a pavilion Khmer, a treehouse English. Only Maths is marked ready.
- **Brand mark:** a teal open book with a lotus-pink bloom and a marigold spark.
  - From 36px: the full mark.
  - Below 36px: a compact drawing.
  - Outlined print files (`brand/print`) and the app icons (`frontend/public`: the mark in paper and lotus on deep river `#10323A`) are rebuilt from the live logo by `brand/tools/build.mjs`.

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
