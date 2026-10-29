/** How many of the fields the English has are written in Khmer: [English, Khmer] pairs. */
export function khmerCount(fields: [string | null | undefined, string | null | undefined][]) {
  const needed = fields.filter(([en]) => en?.trim());
  return { written: needed.filter(([, km]) => km?.trim()).length, needed: needed.length };
}
