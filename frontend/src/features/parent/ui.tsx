import type { ReactNode } from "react";
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

/**
 * A path as a row of book spines: finished (lit), the next one (outlined in light), and the
 * rest. The parent's version of the path of books the child walks.
 */
export function BookStrip({ done, total, label, next = true }: { done: number; total: number; label: string; next?: boolean }) {
  return (
    <div role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label} className="flex items-end gap-[3px]">
      {Array.from({ length: total }, (_, i) => {
        const isDone = i < done;
        const isNext = next && i === done;
        return (
          <span
            key={i}
            aria-hidden
            className={cn(
              "w-3 rounded-[3px] transition-colors",
              i % 3 === 1 ? "h-7" : i % 3 === 2 ? "h-6" : "h-[1.625rem]",
              isDone && "bg-light shadow-[inset_0_-3px_0_rgba(138,74,11,0.25)]",
              isNext && "bg-white ring-2 ring-inset ring-light",
              !isDone && !isNext && "bg-tonle-100",
            )}
          />
        );
      })}
    </div>
  );
}

/** A page opening: the same place the child learns in, seen in calm daylight. */
export function SceneBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn("relative overflow-hidden rounded-[28px] bg-tonle-50 ring-1 ring-line/60", className)}>
      <div aria-hidden className="absolute inset-0">
        <BlueHour time="morning" crop="center" frame={false} fireflies={false} className="absolute inset-y-0 right-0 w-full sm:w-[72%]" />
        <div className="absolute inset-0 bg-[linear-gradient(90deg,#fff_0%,#fff_34%,rgba(255,255,255,0.72)_52%,rgba(255,255,255,0)_78%)] max-sm:bg-[linear-gradient(180deg,rgba(255,255,255,0.2)_0%,rgba(255,255,255,0.92)_58%,#fff_100%)]" />
      </div>
      <div className="relative">{children}</div>
    </section>
  );
}
