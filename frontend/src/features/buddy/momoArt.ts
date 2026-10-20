import type { BuddyMood } from "./kinds";

/**
 * Where the owner's approved Momo artwork plugs in (Y44). Momo is drawn after the owner's Figma character
 * (Momo.tsx); when the approved image of a mood is exported from Figma and saved as
 * `src/assets/buddy/momo-<mood>.webp` (or .png) - idle, happy, cheer, encourage, thinking - it is used for that mood
 * instead, everywhere Momo appears, with no other change. Square images, transparent, Momo standing on the bottom
 * edge, at least 400 px.
 */
const files = import.meta.glob("/src/assets/buddy/momo-*.{webp,png}", { eager: true, query: "?url", import: "default" }) as Record<
  string,
  string
>;

const byMood = new Map<string, string>(
  Object.entries(files).map(([path, url]) => [path.replace(/^.*momo-(\w+)\.(webp|png)$/, "$1"), url]),
);

/** The approved image for this mood, if the owner's artwork has been added; otherwise null (the drawing is used). */
export function momoArt(mood: BuddyMood): string | null {
  return byMood.get(mood) ?? null;
}
