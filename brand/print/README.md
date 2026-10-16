# Bookeyond Kid — print and partner artwork

Outlined vector files: no font needed to open, print or place them. Rebuilt for the River Garden
identity (Y33) from the live logo by `brand/tools/build.mjs`.

| File | Use |
|---|---|
| `bookeyond-kid-logo-ink.svg` / `.pdf` | Mark + wordmark on light grounds (white, paper `#F4F6F1`) |
| `bookeyond-kid-logo-paper.svg` | Mark + wordmark on dark grounds (deep river `#10323A` or darker) |
| `bookeyond-kid-wordmark-ink.svg` / `.pdf` | Wordmark alone on light grounds |
| `bookeyond-kid-wordmark-paper.svg` | Wordmark alone on dark grounds |
| `bookeyond-kid-mark-ink.svg` | The mark alone, 36px / 10mm and larger |
| `bookeyond-kid-mark-small-ink.svg` | The simplified mark for anything smaller (favicons, stamps, small print) |

- "Bookeyond" is Alegreya at weight 750, "Kid" at 420, with the logo's tracking (-0.012em and
  -0.008em) and kerning, outlined from the self-hosted font the app ships
  (`frontend/node_modules/@fontsource-variable/alegreya`, SIL Open Font License).
- Colours on light: Bookeyond `#10323A`, Kid `#22636E`; book `#154049`, lotus `#D9667F` with petals
  `#F0A7B7`, spark `#F2A93B`. On dark: `#FFFFFF` and `#BCD8DA`; book white, lotus `#F0A7B7` with
  petals `#FBD3DC`, spark `#FFD27A`.
- Flat fills only: no shadows, gradients, transparency effects or backgrounds (the one soft inner
  petal is a 55% tint of the petal colour, which prints as a flat tint). The files carry no
  background, so they sit on whatever ground they are placed on.
- Keep clear space of at least the spark's height around the logo. Don't recolour the spark,
  stretch, outline or add effects. Rules in `DESIGN.md`.
- Colours are defined for screen (sRGB). A printer's CMYK/Pantone match has not been proofed; the
  lotus pinks and the marigold spark are the colours most likely to shift and should be proofed
  first.
