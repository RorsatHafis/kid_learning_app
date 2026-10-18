import { useCallback, useEffect, useState } from "react";
import { childrenApi } from "@/lib/api/endpoints";

/**
 * Break reminders (Y36): how long a child has been learning in one sitting, and whether the break
 * their parent asked for is due. A sitting ends after ten minutes away; it lives in this browser tab
 * only (sessionStorage), so it is never stored about the child anywhere else.
 */
export const AWAY_MS = 10 * 60 * 1000;
/** After "one more", the next suggestion comes this much later. */
export const ONE_MORE_MS = 5 * 60 * 1000;

export interface Sitting {
  start: number;
  last: number;
}

const memory = new Map<string, Sitting>();
const key = (childId: string) => `bk.sitting.${childId}`;

export function readSitting(childId: string): Sitting | null {
  try {
    const raw = sessionStorage.getItem(key(childId));
    if (raw) return JSON.parse(raw) as Sitting;
  } catch {
    // Storage unavailable: fall back to this page's memory.
  }
  return memory.get(childId) ?? null;
}

function writeSitting(childId: string, sitting: Sitting | null) {
  if (sitting) memory.set(childId, sitting);
  else memory.delete(childId);
  try {
    if (sitting) sessionStorage.setItem(key(childId), JSON.stringify(sitting));
    else sessionStorage.removeItem(key(childId));
  } catch {
    // Kept in memory only.
  }
}

/** The sitting after the child does something now: a new one if they were away long enough. */
export function touched(previous: Sitting | null, now: number): Sitting {
  if (!previous || now - previous.last > AWAY_MS) return { start: now, last: now };
  return { start: previous.start, last: now };
}

/** Whether a break is due: only when the parent set one, and only once the sitting is that long. */
export function breakDue(minutes: number | null, sitting: Sitting | null, now: number): boolean {
  return minutes !== null && sitting !== null && now - sitting.start >= minutes * 60_000;
}

/** Minutes learnt in this sitting, for the child's message. */
export function sittingMinutes(sitting: Sitting | null, now: number): number {
  return sitting ? Math.max(1, Math.round((now - sitting.start) / 60_000)) : 0;
}

const reminderCache = new Map<string, number | null>();

/**
 * The parent's break setting for this child, and the actions the suggestion offers. A failed read
 * means no suggestion (it is a nudge, never a gate on learning).
 */
export function useBreakReminder(childId: string) {
  const [minutes, setMinutes] = useState<number | null>(reminderCache.get(childId) ?? null);

  useEffect(() => {
    let cancelled = false;
    childrenApi
      .breakReminder(childId)
      .then((r) => {
        reminderCache.set(childId, r.minutes);
        if (!cancelled) setMinutes(r.minutes);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [childId]);

  const touch = useCallback(() => writeSitting(childId, touched(readSitting(childId), Date.now())), [childId]);
  const rest = useCallback(() => writeSitting(childId, null), [childId]);
  const oneMore = useCallback(() => {
    const now = Date.now();
    if (minutes === null) return;
    // Ask again a little later, not straight away after the next activity.
    writeSitting(childId, { start: now - Math.max(0, minutes * 60_000 - ONE_MORE_MS), last: now });
  }, [childId, minutes]);

  return { minutes, touch, rest, oneMore };
}
