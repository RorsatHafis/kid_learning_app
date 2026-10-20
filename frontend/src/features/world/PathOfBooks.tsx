import type { JourneyStep } from "@/lib/api/types";

/** All the path drawing needs from a step: which one it is and whether it is done. */
export type PathStep = Pick<JourneyStep, "learningPathItemId" | "status">;

/**
 * The books of a child's journey (drawn on the completion page's strip, PathStrip): finished books open-cream and
 * warm, the current one marigold, the ones ahead in river teal, each numbered in Khmer digits. Purely visual - the
 * readable journey is the list beside it.
 */
type Point = { x: number; y: number };

const KHMER_DIGITS = "០១២៣៤៥៦៧៨៩";
export const khmerNumber = (n: number) => String(n).replace(/\d/g, (d) => KHMER_DIGITS[Number(d)]);

export type BookState = "done" | "current" | "later";

/** Which steps the scene shows: a couple behind the child, the current one, and what's ahead. */
export function visibleWindow<T extends PathStep>(steps: T[], currentIndex: number, size = 8) {
  const anchor = currentIndex < 0 ? steps.length : currentIndex;
  const start = Math.max(0, Math.min(anchor - 2, steps.length - size));
  return steps.slice(start, start + size).map((step, i) => ({ step, index: start + i }));
}

const BOOK = {
  done: { top: "#F7E7C4", side: "#C99A5E", spine: "#B5552E", ink: "#6B3A12", page: "#FFF8E6" },
  current: { top: "#FFD27A", side: "#E0782F", spine: "#C2551E", ink: "#5A2F07", page: "#FFF3D6" },
  later: { top: "#A9CBCB", side: "#5E9196", spine: "#3E7479", ink: "#1D4950", page: "#D6E7E6" },
} as const;

export function Book({ at, scale, state, number }: { at: Point; scale: number; state: BookState; number: number }) {
  const w = 150 * scale;
  const h = 44 * scale;
  const t = 15 * scale;
  const c = BOOK[state];
  const x = at.x - w / 2;
  const y = at.y - h;
  return (
    <g>
      {/* contact shadow on the path */}
      <ellipse cx={at.x + 6 * scale} cy={at.y + t + 2 * scale} rx={w * 0.56} ry={h * 0.32} fill="#5C4A2A" opacity="0.18" />
      {state === "current" && <ellipse cx={at.x} cy={at.y - h * 0.2} rx={w * 1.05} ry={h * 2.1} fill="url(#path-light)" />}
      <rect x={x} y={at.y - h * 0.15} width={w} height={t + h * 0.15} rx={h * 0.3} fill={c.side} />
      <path d={`M${x + w * 0.16} ${at.y + t * 0.45} L${x + w - h * 0.3} ${at.y + t * 0.45}`} stroke={c.page} strokeWidth={1.6 * scale} opacity="0.85" />
      <rect x={x} y={y} width={w} height={h} rx={h * 0.32} fill={c.top} stroke={state === "current" ? "#FFF3D6" : "none"} strokeWidth={2.5 * scale} />
      <rect x={x} y={y} width={w * 0.13} height={h} rx={h * 0.32} fill={c.spine} />
      {scale > 0.55 && (
        <text
          x={at.x + w * 0.06}
          y={y + h * 0.7}
          textAnchor="middle"
          fontFamily="Kantumruy Pro Variable, sans-serif"
          fontWeight="700"
          fontSize={h * 0.62}
          fill={c.ink}
        >
          {khmerNumber(number)} · {number}
        </text>
      )}
    </g>
  );
}
