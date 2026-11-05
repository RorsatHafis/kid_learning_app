import { useEffect, useState } from "react";

// What a progress display last showed in this tab, by what it shows (memory only: never stored, gone on reload).
const seen = new Map<string, number>();

/**
 * EXP-1: animate what changed, not what is. Returns how many were done when this progress (by `key`) was last shown
 * in this tab, if it has grown since - so the steps lit since then can light up as the child comes back - else null
 * (first sight, no change, or no key). The count itself always comes from the server.
 */
export function useGrowth(key: string | null, done: number): number | null {
  const [from] = useState(() => {
    if (!key) return null;
    const before = seen.get(key);
    return before !== undefined && before < done ? before : null;
  });
  useEffect(() => {
    if (key) seen.set(key, done);
  }, [key, done]);
  return from;
}

/** Tests only: forget what was shown. */
export function forgetGrowth() {
  seen.clear();
}
