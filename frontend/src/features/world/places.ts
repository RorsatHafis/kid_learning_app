import type { SubjectMotif } from "@/features/learning/subjectTheme";
import mathsBridge from "@/assets/world/maths-bridge.webp";
import khmerPavilion from "@/assets/world/khmer-pavilion.webp";
import englishTreehouse from "@/assets/world/english-treehouse.webp";
import lotusPond from "@/assets/world/lotus-pond.webp";

/**
 * Each subject's place in the river village (Y44), from the owner's Figma artwork: Maths at the bridge, Khmer at the
 * pavilion, English in the treehouse, and every other subject at the lotus pond. A place is only ever a subject the
 * child really has; nothing is invented for decoration. `anchor` is where its landmark stands in the village
 * painting (river-village.webp), in percent of the painting's width and height.
 */
export type PlaceKey = "bridge" | "pavilion" | "treehouse" | "pond";

export interface Place {
  key: PlaceKey;
  art: string;
  anchor: { x: number; y: number };
}

const PLACES: Record<PlaceKey, Place> = {
  bridge: { key: "bridge", art: mathsBridge, anchor: { x: 43, y: 46 } },
  pavilion: { key: "pavilion", art: khmerPavilion, anchor: { x: 74, y: 31 } },
  treehouse: { key: "treehouse", art: englishTreehouse, anchor: { x: 90, y: 38 } },
  pond: { key: "pond", art: lotusPond, anchor: { x: 71, y: 60 } },
};

const BY_MOTIF: Record<SubjectMotif, PlaceKey> = {
  maths: "bridge",
  khmer: "pavilion",
  english: "treehouse",
  science: "pond",
  arts: "pond",
  book: "pond",
};

export function placeFor(motif: SubjectMotif): Place {
  return PLACES[BY_MOTIF[motif]];
}

/**
 * Places for a list of subjects in order. Two subjects at the same landmark (two Maths journeys, say) stand one
 * below the other rather than on top of each other.
 */
export function placesFor(motifs: SubjectMotif[]): Place[] {
  const seen = new Map<PlaceKey, number>();
  return motifs.map((motif) => {
    const place = placeFor(motif);
    const n = seen.get(place.key) ?? 0;
    seen.set(place.key, n + 1);
    return n === 0 ? place : { ...place, anchor: { x: place.anchor.x, y: Math.min(place.anchor.y + n * 11, 88) } };
  });
}
