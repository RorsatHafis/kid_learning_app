import { useEffect, useRef, useState } from "react";
import { progressApi } from "@/lib/api/endpoints";
import type { StreakSummary } from "@/lib/api/types";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { childMessages } from "./messages";
import { chip } from "./chips";
import { StreakFlame } from "./StreakFlame";
import { streakChanged, streakGrowth, useStreak, type StreakGrowth } from "./streak";

/**
 * STREAK-1, in the child's space header: the flame and the days in a row. No current streak: an unlit ember and no
 * number (its label says how to light it). When the number grows while the page is open, the flame swells once.
 */
export function StreakChip({ childId }: { childId: string }) {
  const streak = useStreak(childId);
  const m = useMessages(childMessages).streak;
  const days = streak.data?.currentStreak ?? 0;
  const previous = useRef<number | null>(null);
  const [growing, setGrowing] = useState(false);
  useEffect(() => {
    if (!streak.data) return;
    if (previous.current !== null && days > previous.current) {
      setGrowing(true);
      const done = window.setTimeout(() => setGrowing(false), 900);
      previous.current = days;
      return () => window.clearTimeout(done);
    }
    previous.current = days;
  }, [days, streak.data]);
  if (!streak.data) return null;
  const lit = days > 0;
  const label = lit ? m.chip(days) : m.chipNone;
  return (
    <span role="img" aria-label={label} title={label} className={cn(chip, "gap-1 pl-2 pr-3", !lit && "pr-2")}>
      <StreakFlame lit={lit} size={20} growing={growing} key={growing ? "grow" : "steady"} />
      {lit && <span className="tabular text-sm font-bold text-[#B4441A]">{days}</span>}
    </span>
  );
}

/**
 * On the screen after an activity: when finishing it made today a learning day, say so with the flame growing -
 * lit for the first day, grown, or a milestone. Read from the server before and after (never guessed); nothing
 * shows when today already counted or the streak can't be read. Tells the header chip to read again.
 */
export function StreakMoment({ childId, before }: { childId: string; before: StreakSummary | null }) {
  const [growth, setGrowth] = useState<StreakGrowth | null>(null);
  const m = useMessages(childMessages).streak;
  useEffect(() => {
    let cancelled = false;
    progressApi
      .streak(childId)
      .then((after) => {
        if (cancelled) return;
        setGrowth(streakGrowth(before, after));
        streakChanged();
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
    // Read once, when the finished activity's screen opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [childId]);
  if (!growth) return null;
  const milestone = growth.kind === "milestone";
  return (
    <div
      role="status"
      className={cn(
        "mx-auto mt-6 flex w-fit max-w-full items-center gap-3 rounded-[20px] px-5 py-3 text-left ring-1 motion-safe:animate-pop-in",
        milestone ? "bg-[linear-gradient(135deg,#FFF1D6,#FFE0B8)] ring-[#F2A65A]" : "bg-[#FFF6E8] ring-[#F6C98E]",
      )}
    >
      <StreakFlame lit growing size={milestone ? 46 : 34} />
      <span className="min-w-0">
        <span className="block font-display text-lg font-bold leading-snug text-tonle-950">
          {growth.kind === "lit" ? m.lit : milestone ? m.milestone(growth.days) : m.grew(growth.days)}
        </span>
        <span className="block text-sm text-ink-soft">
          {growth.kind === "lit" ? m.litBody : growth.best > growth.days ? m.best(growth.best) : m.days(growth.days)}
        </span>
      </span>
    </div>
  );
}

/**
 * STREAK-1 on the child's home and shelf: the flame, a little bigger than in the header, with the real current streak
 * as the server counts it. Lit: the days in a row, glowing; whether today counts yet is said as an invitation, never
 * as something to lose. No streak: an unlit ember and how to light it. No best-ever count (owner, 2026-11-05) and no
 * repeat of the completion celebration - that stays on the screen after an activity. Nothing while it can't be read.
 */
export function StreakCard({ childId, className }: { childId: string; className?: string }) {
  const streak = useStreak(childId);
  const m = useMessages(childMessages).streak;
  if (!streak.data) return null;
  const days = streak.data.currentStreak;
  const lit = days > 0;
  return (
    <p
      className={cn(
        "flex w-fit max-w-full items-center gap-3 rounded-[20px] py-2 pl-2.5 pr-4 ring-1",
        lit ? "bg-[#FFF6E8] ring-[#F6C98E]" : "bg-white/85 ring-tonle-950/10",
        className,
      )}
    >
      <StreakFlame lit={lit} size={34} />
      <span className="min-w-0 leading-snug">
        <span className="block font-display text-base font-bold text-tonle-950">{lit ? m.inARow(days) : m.lightIt}</span>
        <span className="block text-sm font-medium text-ink-soft">
          {lit ? (streak.data.learnedToday ? m.glowingToday : m.growToday) : m.lightItHow}
        </span>
      </span>
    </p>
  );
}
