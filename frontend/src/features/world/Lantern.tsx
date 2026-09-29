import { useId } from "react";
import { cn } from "@/lib/utils/cn";

export type LanternState = "unlit" | "current" | "lit";

/**
 * A paper lantern from the river at dusk. In an activity each question is a lantern:
 * it lights when the question is answered, so a child can see how far they have come
 * and how much is left — the activity's own progress, drawn in the world's language.
 */
export function Lantern({ state, size, className }: { state: LanternState; size?: number; className?: string }) {
  const uid = useId().replace(/:/g, "");
  const lit = state === "lit";
  return (
    <svg
      viewBox="0 0 40 64"
      width={size}
      height={size ? (size * 64) / 40 : undefined}
      aria-hidden
      className={cn("overflow-visible", state === "current" && "origin-top motion-safe:animate-sway", lit && "motion-safe:animate-lantern-lit", className)}
    >
      <defs>
        <radialGradient id={`${uid}-glow`}>
          <stop offset="0" stopColor="#FFE9A8" stopOpacity="0.85" />
          <stop offset="0.45" stopColor="#FFD27A" stopOpacity="0.35" />
          <stop offset="1" stopColor="#FFD27A" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={`${uid}-body`} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#F3A83B" />
          <stop offset="0.5" stopColor="#FFE3A3" />
          <stop offset="1" stopColor="#F3A83B" />
        </linearGradient>
      </defs>
      {lit && <circle cx="20" cy="30" r="30" fill={`url(#${uid}-glow)`} />}
      <line x1="20" y1="-6" x2="20" y2="9" stroke={lit ? "#E9D3AC" : "#6E8FD0"} strokeWidth="1.2" strokeOpacity={lit ? 0.9 : 0.6} />
      <rect x="13.5" y="8.5" width="13" height="4.5" rx="1.5" fill={lit ? "#8A4A0B" : "#0A1533"} />
      <path
        d="M20 12.5 C36.5 12.5 36.5 48 20 48 C3.5 48 3.5 12.5 20 12.5 Z"
        fill={lit ? `url(#${uid}-body)` : state === "current" ? "#2E4F9E" : "#223D85"}
        stroke={lit ? "#E0782F" : state === "current" ? "#FFD27A" : "#4468B8"}
        strokeOpacity={lit ? 0.55 : state === "current" ? 0.9 : 0.55}
        strokeWidth={state === "current" ? 1.6 : 1}
      />
      <g stroke={lit ? "#E0782F" : "#4468B8"} strokeOpacity={lit ? 0.45 : 0.5} strokeWidth="1" fill="none">
        <path d="M20 13 L20 47.5" />
        <path d="M13 14 C8.5 22 8.5 38.5 13 46.5" />
        <path d="M27 14 C31.5 22 31.5 38.5 27 46.5" />
        <path d="M8 24 C16 26 24 26 32 24" />
        <path d="M8 36.5 C16 34.5 24 34.5 32 36.5" />
      </g>
      <rect x="14.5" y="47.5" width="11" height="4" rx="1.5" fill={lit ? "#8A4A0B" : "#0A1533"} />
      <path d="M20 51.5 L20 58" stroke={lit ? "#E0782F" : "#4468B8"} strokeWidth="1.4" />
      <path d="M17.5 58 L22.5 58 L21.5 63 L18.5 63 Z" fill={lit ? "#E0782F" : "#2E4F9E"} />
    </svg>
  );
}

/**
 * The activity's questions as lanterns on a string across the top of the stage.
 * Answered questions are lit; the one being worked on sways.
 */
export function LanternString({ total, lit, current, label }: { total: number; lit: number; current: number | null; label: string }) {
  // As large as the row allows: big lanterns for a short activity, smaller for a long one.
  const width = `clamp(16px, ${(58 / total).toFixed(1)}vw, ${total > 8 ? 26 : total > 5 ? 34 : 44}px)`;
  return (
    <div role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={lit} aria-label={label} className="flex justify-center">
      <div className="relative flex items-start gap-3 px-4 sm:gap-5">
        <svg aria-hidden className="absolute -top-2.5 left-0 h-6 w-full overflow-visible" preserveAspectRatio="none" viewBox="0 0 100 10">
          <path d="M0 0 Q50 13 100 0" stroke="#C9D6EF" strokeOpacity="0.5" strokeWidth="1" fill="none" vectorEffect="non-scaling-stroke" />
        </svg>
        {Array.from({ length: total }, (_, i) => {
          // Hang along the curve of the string: lower in the middle, higher at the ends.
          const t = total === 1 ? 0.5 : i / (total - 1);
          const drop = Math.round(Math.sin(Math.PI * t) * 7);
          return (
            <span key={i} className="block" style={{ marginTop: drop, width }}>
              <Lantern state={i < lit ? "lit" : i === current ? "current" : "unlit"} className="block h-auto w-full" />
            </span>
          );
        })}
      </div>
    </div>
  );
}
