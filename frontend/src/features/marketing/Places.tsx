import { placeFor } from "@/features/world/places";

/**
 * Places in the river village, one for each subject (Y44): the owner's painted Figma artwork - the stone bridge with
 * its book (Maths), the Khmer pavilion, the English treehouse - the same art the child sees in their world.
 */
export type PlaceKind = "bridge" | "pavilion" | "treehouse";

const MOTIF = { bridge: "maths", pavilion: "khmer", treehouse: "english" } as const;

export function Place({ kind, className }: { kind: PlaceKind; className?: string }) {
  return <img src={placeFor(MOTIF[kind]).art} alt="" aria-hidden className={className} loading="lazy" draggable={false} />;
}
