import type { KhmerName, KhmerNameBody } from "@/lib/api/studio";

/** How many of the fields the English has are written in Khmer: [English, Khmer] pairs. */
export function khmerCount(fields: [string | null | undefined, string | null | undefined][]) {
  const needed = fields.filter(([en]) => en?.trim());
  return { written: needed.filter(([, km]) => km?.trim()).length, needed: needed.length };
}

/**
 * The Khmer to send with a save: only when there was Khmer before or some is written now, so a save that never
 * touched Khmer sends none (and leaves it as it is). Blank parts are sent as null, which removes them.
 */
export function khmerNameBody(before: KhmerName | undefined, nameKm: string, descriptionKm?: string): KhmerNameBody | undefined {
  const had = !!(before?.name || before?.description);
  if (!had && !nameKm.trim() && !descriptionKm?.trim()) return undefined;
  return descriptionKm === undefined
    ? { name: nameKm.trim() || null }
    : { name: nameKm.trim() || null, description: descriptionKm.trim() || null };
}
