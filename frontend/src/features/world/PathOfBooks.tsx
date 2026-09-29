import type { JourneyStep } from "@/lib/api/types";

/** All the path drawing needs from a step: which one it is and whether it is done. */
export type PathStep = Pick<JourneyStep, "learningPathItemId" | "status">;
import { LearningBuddy } from "@/features/buddy/LearningBuddy";

/**
 * The child's journey drawn into the scene: books laid like stepping stones across the
 * river toward the horizon. Finished books are lit, the current book glows with the firefly
 * above it, the next ones wait in the blue. Drawn in BlueHour's 1200×800 space; purely
 * visual — the readable journey is the list beside it.
 */

type Point = { x: number; y: number };

// The route across the water, near (bottom) to far (horizon).
const P0 = { x: 650, y: 772 };
const P1 = { x: 500, y: 690 };
const P2 = { x: 830, y: 616 };
const P3 = { x: 660, y: 532 };

function bezier(t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * u * P0.x + 3 * u * u * t * P1.x + 3 * u * t * t * P2.x + t * t * t * P3.x,
    y: u * u * u * P0.y + 3 * u * u * t * P1.y + 3 * u * t * t * P2.y + t * t * t * P3.y,
  };
}

const KHMER_DIGITS = "០១២៣៤៥៦៧៨៩";
export const khmerNumber = (n: number) => String(n).replace(/\d/g, (d) => KHMER_DIGITS[Number(d)]);

export type BookState = "done" | "current" | "later";

/** Which steps the scene shows: a couple behind the child, the current one, and what's ahead. */
export function visibleWindow<T extends PathStep>(steps: T[], currentIndex: number, size = 8) {
  const anchor = currentIndex < 0 ? steps.length : currentIndex;
  const start = Math.max(0, Math.min(anchor - 2, steps.length - size));
  return steps.slice(start, start + size).map((step, i) => ({ step, index: start + i }));
}

function Book({ at, scale, state, number }: { at: Point; scale: number; state: BookState; number: number }) {
  const w = 150 * scale;
  const h = 44 * scale;
  const t = 15 * scale;
  const top = { done: "#E9D3AC", current: "#FFD27A", later: "#2E4F9E" }[state];
  const side = { done: "#B8894C", current: "#E0782F", later: "#15295E" }[state];
  const spine = { done: "#9E6C33", current: "#C2551E", later: "#223D85" }[state];
  const x = at.x - w / 2;
  const y = at.y - h;
  return (
    <g>
      {state !== "later" && (
        <ellipse cx={at.x} cy={at.y - h * 0.3} rx={w * (state === "current" ? 1.3 : 0.75)} ry={h * (state === "current" ? 2.8 : 1.3)} fill="url(#path-light)" opacity={state === "current" ? 1 : 0.45} />
      )}
      {state !== "later" && <path d={`M${at.x - w * 0.35} ${at.y + t + 6 * scale} L${at.x + w * 0.35} ${at.y + t + 6 * scale}`} stroke="#FFD27A" strokeWidth={3 * scale} strokeLinecap="round" opacity="0.4" />}
      <rect x={x} y={at.y - h * 0.15} width={w} height={t + h * 0.15} rx={h * 0.3} fill={side} />
      <path d={`M${x + w * 0.16} ${at.y + t * 0.45} L${x + w - h * 0.3} ${at.y + t * 0.45}`} stroke={state === "later" ? "#4468B8" : "#FFF3D6"} strokeWidth={1.6 * scale} opacity="0.8" />
      <rect x={x} y={y} width={w} height={h} rx={h * 0.32} fill={top} stroke={state === "current" ? "#FFF3D6" : "none"} strokeWidth={2.5 * scale} />
      <rect x={x} y={y} width={w * 0.13} height={h} rx={h * 0.32} fill={spine} />
      {scale > 0.55 && (
        <text
          x={at.x + w * 0.06}
          y={y + h * 0.7}
          textAnchor="middle"
          fontFamily="Kantumruy Pro Variable, sans-serif"
          fontWeight="700"
          fontSize={h * 0.62}
          fill={state === "later" ? "#C9D6EF" : "#5A2F07"}
        >
          {khmerNumber(number)} · {number}
        </text>
      )}
    </g>
  );
}

export function PathOfBooks({
  steps,
  currentId,
  showBuddy = true,
}: {
  steps: PathStep[];
  currentId: string | null;
  showBuddy?: boolean;
}) {
  const currentIndex = steps.findIndex((s) => s.learningPathItemId === currentId);
  const shown = visibleWindow(steps, currentIndex);
  const n = shown.length;
  const placed = shown.map(({ step, index }, i) => {
    const t = n <= 1 ? 0.15 : Math.pow(i / (n - 1), 0.85) * 0.94;
    const at = bezier(t);
    const scale = 1 - 0.66 * t;
    const state: BookState = step.status === "COMPLETED" ? "done" : index === currentIndex ? "current" : "later";
    return { step, index, at, scale, state };
  });
  const current = placed.find((p) => p.state === "current");
  // With every book lit, the firefly celebrates at the last one.
  const buddyAt = current ?? (placed.length > 0 && placed.every((p) => p.state === "done") ? placed[placed.length - 1] : undefined);

  return (
    <g>
      <defs>
        <radialGradient id="path-light">
          <stop offset="0" stopColor="#FFE9A8" stopOpacity="0.75" />
          <stop offset="0.5" stopColor="#FFD27A" stopOpacity="0.28" />
          <stop offset="1" stopColor="#FFD27A" stopOpacity="0" />
        </radialGradient>
      </defs>
      {/* a faint wake joining the stones */}
      <path d={`M${P0.x} ${P0.y} C${P1.x} ${P1.y} ${P2.x} ${P2.y} ${P3.x} ${P3.y}`} stroke="#A2B8E3" strokeWidth="2" strokeDasharray="2 14" strokeLinecap="round" fill="none" opacity="0.5" />
      {/* far books first so near ones overlap them */}
      {[...placed].reverse().map((p) => (
        <Book key={p.step.learningPathItemId} at={p.at} scale={p.scale} state={p.state} number={p.index + 1} />
      ))}
      {buddyAt && showBuddy && (
        <g transform={`translate(${buddyAt.at.x - 75 * buddyAt.scale} ${buddyAt.at.y - 215 * buddyAt.scale})`}>
          <LearningBuddy mood={current ? "happy" : "cheer"} size={150 * buddyAt.scale} />
        </g>
      )}
    </g>
  );
}
