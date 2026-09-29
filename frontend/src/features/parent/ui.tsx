import type { ReactNode } from "react";
import type { Chapter } from "@/lib/api/types";
import { BlueHour } from "@/features/world/BlueHour";
import { cn } from "@/lib/utils/cn";

/** Each child keeps one colour across the parent space, chosen from their id so it never shifts. */
const SEALS = [
  "bg-tonle-700 text-white",
  "bg-dusk-deep text-white",
  "bg-palm-deep text-white",
  "bg-lotus-deep text-white",
  "bg-tonle-950 text-light",
];

export function sealFor(id: string) {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return SEALS[h % SEALS.length];
}

/** A child's initial set like a book spine: their mark in the family. */
export function ChildSeal({ id, name, size = "md" }: { id: string; name: string; size?: "md" | "lg" }) {
  return (
    <span
      aria-hidden
      className={cn(
        "relative flex shrink-0 items-center justify-center overflow-hidden rounded-[14px] font-display font-bold shadow-tight",
        "after:absolute after:inset-y-0 after:left-0 after:w-[18%] after:bg-black/15",
        size === "lg" ? "h-20 w-16 text-3xl" : "h-14 w-11 text-xl",
        sealFor(id),
      )}
    >
      <span className="relative translate-x-[3px]">{name.charAt(0).toUpperCase()}</span>
    </span>
  );
}

/** A page opening: the same place the child learns in, seen in calm daylight. */
export function SceneBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn("relative overflow-hidden rounded-[28px] bg-tonle-50 ring-1 ring-line/60", className)}>
      <div aria-hidden className="absolute inset-0">
        <BlueHour time="morning" crop="center" frame={false} fireflies={false} className="absolute inset-x-0 top-0 h-[62%] w-full sm:inset-y-0 sm:left-auto sm:right-0 sm:h-full sm:w-[72%]" />
        <div className="absolute inset-0 bg-[linear-gradient(90deg,#fff_0%,#fff_34%,rgba(255,255,255,0.72)_52%,rgba(255,255,255,0)_78%)] max-sm:bg-[linear-gradient(180deg,rgba(255,255,255,0)_0%,rgba(255,255,255,0)_28%,rgba(255,255,255,0.9)_62%,#fff_100%)]" />
      </div>
      <div className="relative">{children}</div>
    </section>
  );
}

const CHAPTER_WORD: Record<Chapter["state"], string> = {
  COMPLETE: "steps done",
  CURRENT: "in progress",
  STARTED: "started",
  UPCOMING: "not started",
  NOT_READY: "no activity published yet",
};

/**
 * A journey's chapters as a track: each chapter a segment, filled as its steps are done, the
 * current one ringed in light. Reports activity, not mastery.
 */
export function ChapterTrack({ chapters, label }: { chapters: Chapter[]; label: string }) {
  // A one-chapter journey has nothing to track between chapters; its steps say it all.
  if (chapters.length < 2) return null;
  const done = chapters.filter((c) => c.state === "COMPLETE").length;
  const current = chapters.find((c) => c.state === "CURRENT");
  return (
    <div className="flex flex-col gap-2">
      <ol aria-label={label} className="grid gap-1.5" style={{ gridTemplateColumns: `repeat(${chapters.length}, minmax(0, 1fr))` }}>
        {chapters.map((chapter) => {
          const share = chapter.totalSteps > 0 ? chapter.completedSteps / chapter.totalSteps : 0;
          return (
            <li key={chapter.id} title={`Chapter ${chapter.position}: ${chapter.title}`}>
              <span className="sr-only">
                Chapter {chapter.position}, {chapter.title}: {CHAPTER_WORD[chapter.state]}
                {chapter.totalSteps > 0 ? ` (${chapter.completedSteps} of ${chapter.totalSteps} steps)` : ""}
              </span>
              <span
                aria-hidden
                className={cn(
                  "relative block h-2.5 overflow-hidden rounded-full",
                  chapter.state === "NOT_READY" ? "border border-dashed border-line-strong bg-white" : "bg-tonle-100",
                  chapter.state === "CURRENT" && "ring-2 ring-light ring-offset-1 ring-offset-white",
                )}
              >
                <span className="absolute inset-y-0 left-0 rounded-full bg-light" style={{ width: `${Math.round(share * 100)}%` }} />
              </span>
            </li>
          );
        })}
      </ol>
      <p className="text-sm text-ink-soft">
        {current ? (
          <>
            <span className="font-semibold text-ink">
              Chapter {current.position} of {chapters.length}
            </span>{" "}
            · {current.title}
          </>
        ) : done === chapters.length ? (
          <>All {chapters.length} chapters&apos; steps done</>
        ) : (
          <>
            {done} of {chapters.length} chapters&apos; steps done
          </>
        )}
      </p>
    </div>
  );
}
