---
name: Bookeyond Kid
description: Learn Today, Build Tomorrow — a children's learning world at blue hour on the Mekong, with an honest window for parents and a precise workspace for schools.
colors:
  tonle-50: "#F2F5FB"
  tonle-100: "#E3EAF7"
  tonle-200: "#C9D6EF"
  tonle-300: "#A2B8E3"
  tonle-400: "#6E8FD0"
  tonle-500: "#4468B8"
  tonle-600: "#2E4F9E"
  tonle-700: "#223D85"
  tonle-800: "#182E66"
  tonle-900: "#11214D"
  tonle-950: "#0A1533"
  ink: "#0E1B3A"
  ink-soft: "#46526E"
  ink-faint: "#636E88"
  paper: "#F6F7FB"
  paper-raised: "#FFFFFF"
  paper-sunk: "#EDF0F7"
  child-page: "#FFFBF3"
  line: "#DCE2EE"
  line-strong: "#C3CCDD"
  light: "#F3A83B"
  light-soft: "#FCEBD0"
  light-glow: "#FFD27A"
  light-deep: "#8A4A0B"
  lotus: "#E48BA0"
  lotus-deep: "#9C3656"
  palm: "#2E7A5A"
  palm-soft: "#E3F1EA"
  palm-deep: "#1E5A41"
  dusk: "#6D5BA8"
  dusk-deep: "#4A3B86"
  brick: "#C2412F"
  brick-soft: "#FBE8E4"
  brick-deep: "#9E2F20"
typography:
  display:
    fontFamily: "Bricolage Grotesque Variable, system-ui, sans-serif"
    fontSize: "clamp(2.5rem, 1.6rem + 3.6vw, 4.25rem)"
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.02em"
    fontVariation: "\"opsz\" 72"
  headline:
    fontFamily: "Bricolage Grotesque Variable, system-ui, sans-serif"
    fontSize: "clamp(2rem, 1.5rem + 2vw, 2.75rem)"
    fontWeight: 600
    lineHeight: 1.08
    fontVariation: "\"opsz\" 60"
  title:
    fontFamily: "Bricolage Grotesque Variable, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.2
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
  child-page: "32px"
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
    textColor: "{colors.tonle-900}"
    rounded: "{rounded.field}"
    height: "44px"
  button-light:
    backgroundColor: "{colors.light}"
    textColor: "{colors.tonle-950}"
    rounded: "{rounded.pill}"
    height: "64px"
    padding: "0 40px"
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
    backgroundColor: "{colors.tonle-950}"
    textColor: "{colors.tonle-200}"
    width: "248px"
---

# Design System: Bookeyond Kid

## Overview

**Creative North Star: "Blue hour on the Mekong"**

Bookeyond Kid lives in the few minutes after sunset on a Cambodian river: deep Tonle blue sky, sugar palms and temple towers cut from paper, stilt houses with lit windows, lotus in the shallows. Blue is the world, not a coat of paint. The one warm colour is *light*: lanterns, lit books, the firefly buddy's glow. Light means something happened: a book finished, a lantern lit, the next step.

The same world is seen three ways. **Children** are inside it: full-bleed dusk scenes, a path of books, a warm page to read from, big pressable answers and lanterns that light as they work. **Parents** see it in calm daylight: a morning band of the same river above clear, evidence-first pages on cool paper. **Staff** (teachers, principals, administrators) work beside it: a night-blue rail carries the brand, and precise tables and panels sit on quiet paper.

**Key Characteristics:**
- Clearly Cambodian, drawn from scratch: paper-cut planes, Khmer numerals beside Arabic, Kantumruy Pro for text.
- Blue family (Tonle 50–950) for structure; saffron *light* is used only for meaning.
- Evidence is visual: line weight shows how much evidence stands behind a skill.
- Adult controls are rounded rectangles; pills and pressable "keys" belong to the child space.
- Motion shows state: lanterns sway while a question is open, light when answered, rise at the end.

## Colors

A night-to-morning blue scale, cool paper neutrals, and one warm voice.

### Primary
- **Tonle Night** (#0A1533, tonle-950): the dusk sky, the staff rail, the landing nav and footer.
- **Deep River** (#182E66, tonle-800): the solid adult button and active emphasis.
- **River Blue** (#2E4F9E, tonle-600): focus rings, links, progressing evidence.

### Secondary
- **Lantern Light** (#F3A83B, light): lit books and lanterns, the child's "go" button, the landing CTA on night backgrounds. Text on light fills is always tonle-950 or light-deep (#8A4A0B).

### Tertiary
- **Palm** (#2E7A5A / deep #1E5A41): published, secure, available.
- **Lotus** (#E48BA0 / deep #9C3656), **Dusk** (#6D5BA8 / deep #4A3B86): child seals and illustration accents only.
- **Brick** (#C2412F / deep #9E2F20): errors and destructive actions only.

### Neutral
- **Ink** (#0E1B3A), **Ink Soft** (#46526E, 7.4:1 on paper), **Ink Faint** (#636E88, 4.9:1 on paper; not on paper-sunk).
- **Cool Paper** (#F6F7FB) page ground; **Raised** (#FFFFFF) sheets; **Sunk** (#EDF0F7) wells; **Child Page** (#FFFBF3) the book paper a child reads from.
- **Line** (#DCE2EE) dividers; **Line Strong** (#C3CCDD) field strokes.

### Named Rules
**The Light Means Something Rule.** Saffron appears only where something is lit, achieved or next. It is never decoration, never a background wash, never a hover colour in adult space.

**The Never Colour Alone Rule.** Every standing, status or match colour sits beside words or a number (a standing's square plus its name; a pair colour plus its number).

## Typography

**Display Font:** Bricolage Grotesque Variable (optical size axis in use)
**Body Font:** Kantumruy Pro Variable (Latin and Khmer in one family)

**Character:** Bricolage's lively, slightly condensed grotesque gives headings a hand-made confidence. Kantumruy Pro keeps English and Khmer text at the same colour and rhythm.

### Hierarchy
- **Display** (600, clamp 2.5–4.25rem, 1.02, opsz 72): landing hero, a child's question prompt.
- **Headline** (600, clamp 2–2.75rem, 1.08): page openings (Your family, a child's name, Account, section heads on the landing).
- **Title** (600, 1.875–2.125rem): workspace page headers.
- **Body** (400, 0.9375–1.0625rem, 1.6): explanations, max ~70ch.
- **Label** (500, 0.8125rem): captions, table headers, in sentence case.

### Named Rules
**The No Kicker Rule.** No small uppercase eyebrow above a heading. Place context in a breadcrumb or in the sentence under the heading.

## Layout

The family space uses a 1180px container with 32px gutters (16px on phones), with a scene band opening each page. A child's overview page puts detail in a main column and insights in a 340px sticky column. On phones, insights move directly after the current path. Staff pages sit beside a fixed 248px rail (a night-blue top bar with a menu below `lg`) with a 1180px content column. The landing alternates paper and night sections, with 80–112px vertical rhythm. The child space is always full-bleed scene: content floats on it, and nothing ever sits on a flat colour.

## Elevation & Depth

Depth is paper laid on paper. Shadows are soft and tinted with night ink, never grey or black offsets. The child space adds light as depth: glows under lit things.

### Shadow Vocabulary
- **paper** (`0 1px 1px rgba(10,21,51,.05), 0 12px 32px -18px rgba(10,21,51,.35)`): family sheets.
- **lift** (`0 2px 4px rgba(10,21,51,.08), 0 24px 48px -24px rgba(10,21,51,.45)`): the child's page over the scene.
- **glow** (`0 0 0 1px rgba(243,168,59,.45), 0 0 32px 4px rgba(255,210,122,.55)`): lit answers and lit things.
- **key press** (`0 5px 0 <darker tone>`): child answer tiles, number pad, go button; collapses to 1px on press.

### Named Rules
**The Paper Not Plastic Rule.** No hard offset shadows in adult space, no glassmorphism as decoration. Blur appears only on chips floating over the scene.

## Shapes

Adult radii step from 6px (tags) through 10px (controls) and 12px (fields and buttons) to 16px (workspace panels) and 22px (family sheets). The child space is rounder: a 32px page, 26px answer tiles, pill buttons. Borders are 1px hairlines. Colour accents never ride on a thick border edge.

## Components

### Buttons
- **Shape:** adult rounded rectangle (12px; sm 10px, lg 14px).
- **Solid:** Deep River fill, white text, 44px tall. Hover: tonle-900.
- **Outline / Ghost:** white with a line-strong ring / text-only tonle-700.
- **Child "go":** Lantern Light pill, 64px, tonle-950 text, key-press shadow `0 5px 0 #B86A1C`.
- **Focus:** 2px River Blue outline, offset 3px (global `:focus-visible`). Inside `.on-night` surfaces (staff rail, landing night sections, child space) the ring is Lantern Glow `#FFD27A`; `.on-paper` panels inside them return to blue.

### Inputs / Fields
- **Style:** white, 48px, 12px radius, 1px line-strong inset ring. Hover: tonle-300. Focus: 2px tonle-600.
- **Error:** 2px brick ring, message in brick-deep with an icon, linked by aria-describedby.

### Cards / Containers
- **Family sheet:** white, 22px, paper shadow, line/70 ring. Rows divide with 1px lines instead of nesting cards.
- **Workspace panel:** white, 16px, 1px line ring, a header row with a hairline under it. Tables run edge to edge (`flush`) and become stacked lists on phones.
- **Composer:** creation forms (new subject, skill, question, lesson, curriculum, staff account) open from a "New …" action in their list's header as a titled, closable panel (Escape too). The first field takes focus. The composer starts open only while its list is empty and closes once the item exists.

### Page frame
- Every page names the browser tab "Page · Bookeyond Kid" (`useDocumentTitle`; PageHeader and AuthLayout do it for their title). The landing keeps "Bookeyond Kid — Learn Today, Build Tomorrow".
- Family, staff and landing pages start with a "Skip to content" link to `#main`.

### States
- **Loading:** three small books breathing, fading in only after a noticeable wait. Shown only before the first data arrives; a refresh keeps the current content on screen.
- **Empty:** dashed outline with the book glyph, a title and one sentence on what to do next.
- **Error:** "That didn't load", the reason, and "Try again". Child space uses the firefly (encourage) and a pressable retry.

### Navigation
- **Family:** white sticky bar, text links with a 2px underline indicator.
- **Staff:** night rail with Logo (paper tone), workspace name, nav items (10px radius, active white/9% with a light-coloured icon), account and log out at the foot. Sub-pages carry a slash breadcrumb ("School / Grade 1 A").
- **Landing:** solid night bar, light "Get started".

### Signature Components
- **Path of Books:** the child's learning path as books across the river. Lit (done), glowing (current), blue (later), labelled "៥ · 5".
- **Lantern String:** an activity's questions as paper lanterns on a string: unlit, swaying (current), lit (answered).
- **Evidence Line:** evidence strength as line weight: dotted hairline (none), dashed (partial), 3.5px (sufficient), 5.5px (strong). Always has a text label.
- **Firefly Buddy:** an authored character (never a chatbot) with moods happy, thinking, cheer, encourage. It sits on the page corner and reacts.
- **Answer Slot + Number Pad:** a sum written "… = ?" takes its answer where the "?" was; questions in words keep their wording with the field below. The 3×4 pad (digits, delete, Check) is the keyboard on touch screens.
- **Print artwork:** outlined logo and wordmark (SVG ink/paper, PDF ink) and both marks in `brand/print`, generated from the live type by `brand/tools`.
- **Brand mark sizes:** from 36px the full mark (page edges, side petals, inner petal); below 36px a compact drawing (solid book, bold three-petal lotus, clear spark). The favicon uses the compact drawing, home-screen icons the full one.

## Do's and Don'ts

### Do:
- **Do** open family pages with the morning scene band and child pages with the dusk scene.
- **Do** state evidence plainly beside its drawing ("4 of 6 answers correct", "Enough answers to judge").
- **Do** keep Khmer beside English where it carries identity (numerals, subject names, the hero line).
- **Do** use `motion-safe:` for every animation. Reduced motion shows the final state.
- **Do** make hover styles hover-only (`hoverOnlyWhenSupported`).

### Don't:
- **Don't** add leaderboards, streak pressure, points, currencies or confetti. Celebration is light: lanterns, sparks, a lit book.
- **Don't** use saffron for anything that isn't lit, achieved or next.
- **Don't** put uppercase eyebrow labels over headings, gradient text, hero-metric tiles or same-size icon-card grids.
- **Don't** present completion as mastery. Steps completed and skills evidenced are shown separately.
- **Don't** use ink-faint text on paper-sunk (fails 4.5:1).
