# Momo's approved artwork (drop-in)

Momo is drawn in code after the owner's Figma character (`src/features/buddy/Momo.tsx`). To use the approved
artwork instead, export each mood from the Figma file as a square, transparent image (at least 400 px, Momo
standing on the bottom edge) and save it here as:

- `momo-idle.webp` - holding his leaf card
- `momo-happy.webp` - waving
- `momo-cheer.webp` - celebrating
- `momo-encourage.webp` - encouraging after a mistake
- `momo-thinking.webp` - thinking (loading)

`.png` works too. Each file replaces the drawing for its mood everywhere Momo appears (`src/features/buddy/momoArt.ts`);
a mood without a file keeps the drawing.
