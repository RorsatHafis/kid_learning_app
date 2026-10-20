import type { ReactNode } from "react";
import type { Chapter, LearningBuddyKind } from "@/lib/api/types";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { PaintedScene } from "@/features/world/PaintedWorld";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

/** Each child keeps one colour across the parent space, chosen from their id so it never shifts. */
const SEALS = [
  "bg-tonle-700 text-white",
  "bg-dusk-deep text-white",
  "bg-palm-deep text-white",
  "bg-lotus-deep text-white",
  "bg-clay-deep text-white",
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

/** The child's own buddy in a soft frame: the world they play in, seen from the parent's pages. */
export function ChildPortrait({ buddy, size = "md" }: { buddy: LearningBuddyKind; size?: "md" | "lg" }) {
  const box = size === "lg" ? "h-20 w-20 rounded-[24px]" : "h-16 w-16 rounded-[20px]";
  return (
    <span aria-hidden className={cn("flex shrink-0 items-end justify-center overflow-hidden bg-[#EDE6D3] ring-1 ring-tonle-950/5", box)}>
      <LearningBuddy kind={buddy} mood="idle" size={size === "lg" ? 88 : 70} className="!animate-none -mb-1.5" />
    </span>
  );
}

/** A page opening: the same place the child learns in, seen in calm daylight. */
export function SceneBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn("relative overflow-hidden rounded-[28px] bg-tonle-50 shadow-card", className)}>
      <div aria-hidden className="absolute inset-0">
        <div className="absolute inset-x-0 top-0 h-[62%] w-full sm:inset-y-0 sm:left-auto sm:right-0 sm:h-full sm:w-[72%]">
          <PaintedScene position="60% 55%" />
        </div>
        <div className="absolute inset-0 bg-[linear-gradient(90deg,#fff_0%,#fff_34%,rgba(255,255,255,0.72)_52%,rgba(255,255,255,0)_78%)] max-sm:bg-[linear-gradient(180deg,rgba(255,255,255,0)_0%,rgba(255,255,255,0.1)_20%,rgba(255,255,255,0.86)_42%,#fff_64%)]" />
      </div>
      <div className="relative">{children}</div>
    </section>
  );
}


/**
 * A journey's chapters as a track: each chapter a segment, filled as its steps are done, the
 * current one ringed in light. Reports activity, not mastery.
 */
export function ChapterTrack({ chapters, label }: { chapters: Chapter[]; label: string }) {
  // A one-chapter journey has nothing to track between chapters; its steps say it all.
  const m = useMessages(familyMessages).chapters;
  if (chapters.length < 2) return null;
  const done = chapters.filter((c) => c.state === "COMPLETE").length;
  const current = chapters.find((c) => c.state === "CURRENT");
  return (
    <div className="flex flex-col gap-2">
      <ol aria-label={label} className="grid gap-1.5" style={{ gridTemplateColumns: `repeat(${chapters.length}, minmax(0, 1fr))` }}>
        {chapters.map((chapter) => {
          const share = chapter.totalSteps > 0 ? chapter.completedSteps / chapter.totalSteps : 0;
          return (
            <li key={chapter.id} title={m.titleOf(chapter.position, chapter.title)}>
              <span className="sr-only">
                {m.srState(chapter.position, chapter.title, m.word[chapter.state])}
                {chapter.totalSteps > 0 ? m.srSteps(chapter.completedSteps, chapter.totalSteps) : ""}
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
            <span className="font-semibold text-ink">{m.chapterOf(current.position, chapters.length)}</span> · {current.title}
          </>
        ) : done === chapters.length ? (
          m.allDone(chapters.length)
        ) : (
          m.someDone(done, chapters.length)
        )}
      </p>
    </div>
  );
}
