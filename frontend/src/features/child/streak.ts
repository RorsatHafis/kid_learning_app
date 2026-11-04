import { useEffect, useState } from "react";
import { progressApi } from "@/lib/api/endpoints";
import type { StreakSummary } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";

// STREAK-1: the child's learning streak as the server counts it (real finished activities, the child's learning day).

const CHANGED = "bk:streak-changed";

/** An activity was finished: whatever shows the streak reads it again. */
export function streakChanged() {
  window.dispatchEvent(new Event(CHANGED));
}

/** The child's streak, read again whenever an activity is finished. */
export function useStreak(childId: string) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const again = () => setTick((t) => t + 1);
    window.addEventListener(CHANGED, again);
    return () => window.removeEventListener(CHANGED, again);
  }, []);
  return useAsync(() => progressApi.streak(childId), [childId, tick]);
}

/** Days worth a bigger celebration. */
export const MILESTONES = [3, 7, 14, 21, 30, 50, 100, 200, 365];

export type StreakGrowth = { kind: "lit" | "grew" | "milestone"; days: number; best: number };

/**
 * What finishing an activity did to the streak, from the server's streak before and after: lit (the first day), grew,
 * or reached a milestone - only when this finish made today count. Nothing when today already counted or either
 * reading is missing (no celebration is ever guessed).
 */
export function streakGrowth(before: StreakSummary | null, after: StreakSummary | null): StreakGrowth | null {
  if (!before || !after || before.learnedToday || !after.learnedToday || after.currentStreak < 1) return null;
  const days = after.currentStreak;
  const kind = days === 1 ? "lit" : MILESTONES.includes(days) ? "milestone" : "grew";
  return { kind, days, best: after.longestStreak };
}
