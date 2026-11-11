# Artwork still needed (WORLD-1) - a brief for the illustrator

> Three pieces complete the child's world. Everything else is built: once a file arrives it is one entry in
> `frontend/src/features/world/places.ts` (painting, light, landmark, layers) - no new drawing code.
> Style: **the existing Figma set** (the river village, the bridge, the pavilion, the treehouse) - soft painted
> watercolour/gouache, warm morning light, Cambodian river-village landscape, calm and premium, never neon or busy.

## The existing set, for reference (in `frontend/src/assets/world/`)

| File | Size (px) | Role |
|---|---|---|
| `river-village.webp` / `-sm` | 1584 x 672 / 900 x 382 | A place's painted backdrop (the shape every new backdrop follows) |
| `river-bend.webp` / `-sm` | 1584 x 672 / 900 x 382 | Backdrop |
| `maths-bridge.webp`, `khmer-pavilion.webp`, `english-treehouse.webp` | 640 x 516 | Landmarks: the object standing sharp in front, transparent background |
| `science-garden.webp` | 386 x 625 | Landmark (tall) |
| `open-river.webp` | 512 x 217 | The only open-river export - too small (see 1) |

## What to deliver

### 1. Open river - panorama backdrop
- **Sizes**: 1584 x 672 and 900 x 382 (the same picture at both sizes).
- **Content**: the wide open river - water across most of the frame, far bank and sky; no landmark, no text.
- **Why**: the arts island stands in the open water; today the 512 px export is blown up and looks soft on large
  screens.

### 2. Garden - backdrop
- **Sizes**: 1584 x 672 and 900 x 382.
- **Content**: a garden on the river bank (vegetable beds, flowers, a path), seen from where the sapling stands; no
  landmark (the sapling stands in front), no text.
- **Why**: the science garden borrows the river path's bank today.

### 3. Rice fields - a new place (owner's list)
- **Backdrop**: 1584 x 672 and 900 x 382 - paddies stepping toward palms and a far village, water glints in the
  fields, morning light; no landmark, no text.
- **Landmark**: ~640 px wide (height to suit, like the bridge at 640 x 516), **transparent background** - for
  example a small rice-field hut on stilts with a buffalo or a scarecrow. It stands in front, bottom right, and
  must read clearly at about 200 px wide on a phone.
- **Owner decision with it**: which subject the rice fields belong to (today: maths at the bridge, Khmer at the
  pavilion, English in the treehouse, science in the garden, the arts on the island, everything else at the pond).

## Composition rules (all backdrops)

- The child's heading and buttons sit over the **left third**: keep it calm (sky, water, soft distance).
- The landmark stands at the **bottom right** (about 60-85 % of the scene's height): keep that corner simple so the
  landmark reads.
- The picture is cropped to fit wide and narrow screens - keep anything important inside the **middle 70 %** of
  the width.
- Ambient life (clouds drifting, glints, falling leaves) is added by code - do not paint motion blur or effects.

## Files

- **Format**: PNG (transparency for the landmark) or high-quality JPEG/PNG for backdrops; sRGB. We convert to WebP.
- **Budget after conversion**: backdrops <= 200 KB at 1584 px, <= 80 KB at 900 px; landmark <= 80 KB.
- **Source**: the Figma frames (or layered PSD) as well, so later crops and seasons can be made.
- **Rights**: Bookeyond needs the right to use the pictures in the app and on the public pages
  [OWNER: the illustrator's licence or assignment].

## Acceptance (what we check when the files arrive)

1. The scene renders in `/__play` (the dev gallery) at 1280, 768 and 360 px wide with no awkward crop.
2. Heading text over the left third keeps WCAG AA contrast (checked by the automated accessibility test).
3. Sizes within the budget; the landmark's edges are clean on light and dark water.
