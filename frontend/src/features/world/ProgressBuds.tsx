import { cn } from "@/lib/utils/cn";

/**
 * An activity's questions as a quiet row of lotus buds: a bud for each question, open once it is
 * answered, the one being worked on standing a little taller. The only progress a child sees while
 * answering - small, calm, and never a score.
 */
export function ProgressBuds({ total, done, current, label }: { total: number; done: number; current: number | null; label: string }) {
  const size = total > 10 ? 22 : total > 6 ? 28 : 34;
  return (
    <div role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label={label} className="flex justify-center">
      <div className="flex flex-wrap items-end justify-center gap-1.5 rounded-pill bg-white/85 px-4 py-2 shadow-tight ring-1 ring-tonle-950/5 sm:gap-2">
        {Array.from({ length: total }, (_, i) => (
          <Bud key={i} state={i < done ? "open" : i === current ? "current" : "waiting"} size={size} />
        ))}
      </div>
    </div>
  );
}

export function Bud({ state, size = 26, className }: { state: "open" | "current" | "waiting"; size?: number; className?: string }) {
  const open = state === "open";
  return (
    <svg
      viewBox="0 0 32 32"
      width={size}
      height={size}
      aria-hidden
      className={cn("block transition-transform duration-300 ease-out", state === "current" && "-translate-y-0.5 scale-110", open && "motion-safe:animate-pop-in", className)}
    >
      <path d="M16 31 L16 24" stroke={open ? "#4E8F45" : "#8DBDC2"} strokeWidth="2" strokeLinecap="round" />
      {open ? (
        <g>
          <path d="M16 25 C9 24 4 19 4 12 C9 13 13 17 16 25 Z" fill="#F0A7B7" />
          <path d="M16 25 C23 24 28 19 28 12 C23 13 19 17 16 25 Z" fill="#F0A7B7" />
          <path d="M16 25 C11 20 11 11 16 4 C21 11 21 20 16 25 Z" fill="#F6C3CE" />
          <ellipse cx="16" cy="19" rx="3" ry="2" fill="#F2C14E" />
        </g>
      ) : (
        <path
          d="M16 25 C10 22 10 12 16 5 C22 12 22 22 16 25 Z"
          fill={state === "current" ? "#F6C3CE" : "#DCEBEC"}
          stroke={state === "current" ? "#D9667F" : "#8DBDC2"}
          strokeWidth="1.4"
        />
      )}
    </svg>
  );
}
