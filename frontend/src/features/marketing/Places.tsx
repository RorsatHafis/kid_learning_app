import { placeByKey, type PlaceKey } from "@/features/world/places";

/**
 * Places in the river village, one for each subject (Y44, WORLD-1): the owner's painted Figma artwork - the stone
 * bridge with its book (Maths), the Khmer pavilion, the English treehouse, the science garden, the arts island, the
 * lotus pond - the same art the child sees in their world.
 */
export type PlaceKind = PlaceKey;

export function Place({ kind, className }: { kind: PlaceKind; className?: string }) {
  return <img src={placeByKey(kind).art} alt="" aria-hidden className={className} loading="lazy" draggable={false} />;
}
