import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { Book, visibleWindow, type BookState, type PathStep } from "./PathOfBooks";

const GAP = 118; // between book centres
const PAD = 70;
const WATER_Y = 104;
const SCALE = 0.56;
const SLOTS = 6; // books the river shows at most

/**
 * The child's chapter as a short stretch of the village path: its books in play order. Drawn on
 * the completion page so finishing an activity happens *on the path*: the book just finished takes
 * the light where it sits, and the buddy walks on to the next one. When the activity closed the
 * chapter (`chapterDone`), the whole row takes the light in turn. It shows only what the server's
 * path says (each step's status, the next step); decorative — the page says the same in words.
 */
export function PathStrip({
  steps,
  justLitId,
  nextId,
  chapterDone = false,
}: {
  steps: PathStep[];
  /** The step this activity finished: drawn lighting up. */
  justLitId: string | null;
  /** Where the buddy goes next; null when nothing waits in this chapter. */
  nextId: string | null;
  chapterDone?: boolean;
}) {
  const lit = steps.findIndex((s) => s.learningPathItemId === justLitId);
  const next = steps.findIndex((s) => s.learningPathItemId === nextId);
  const shown = visibleWindow(steps, next >= 0 ? next : lit >= 0 ? lit + 1 : steps.length, SLOTS);
  // The river spans the books shown (at least three places), drawn at a fixed height: books keep
  // one readable size on a phone and a desk, and only a long chapter scales them down to fit.
  const width = PAD * 2 + (Math.max(shown.length, 3) - 1) * GAP;
  const first = (width - Math.max(shown.length - 1, 0) * GAP) / 2;
  const xOf = (index: number) => first + shown.findIndex((s) => s.index === index) * GAP;

  const at = (i: number) => ({ x: first + i * GAP, y: WATER_Y });
  const buddyIndex = next >= 0 && shown.some((s) => s.index === next) ? next : lit >= 0 ? lit : null;
  const hopFrom =
    lit >= 0 && buddyIndex !== null && buddyIndex !== lit && shown.some((s) => s.index === lit) ? xOf(lit) : null;

  return (
    <svg
      viewBox={`0 0 ${width} 150`}
      preserveAspectRatio="xMidYMid meet"
      className="mx-auto block h-32 w-full overflow-visible sm:h-40"
      aria-hidden
    >
      <defs>
        <radialGradient id="path-light">
          <stop offset="0" stopColor="#FFE9A8" stopOpacity="0.75" />
          <stop offset="0.5" stopColor="#FFD27A" stopOpacity="0.28" />
          <stop offset="1" stopColor="#FFD27A" stopOpacity="0" />
        </radialGradient>
        <linearGradient id="strip-path" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#EADBB8" />
          <stop offset="1" stopColor="#DCC799" />
        </linearGradient>
      </defs>
      {/* the path the books rest on, running on past them to the panel's edges (it clips),
          with grass along its edges */}
      <rect x={-width * 3} y={WATER_Y - 14} width={width * 7} height="44" rx="22" fill="url(#strip-path)" />
      <path d={`M${-width * 3} ${WATER_Y - 14} H${width * 4}`} stroke="#8FA88A" strokeWidth="3" opacity="0.6" />
      <path d={`M${-width * 3} ${WATER_Y + 30} H${width * 4}`} stroke="#8FA88A" strokeWidth="3" opacity="0.6" />

      {shown.map(({ step, index }, i) => {
        const state: BookState = step.status === "COMPLETED" ? "done" : index === next ? "current" : "later";
        const takingLight = index === lit || (chapterDone && state === "done");
        // In a finished chapter the row lights in turn, the book just finished last.
        const delay = chapterDone ? 250 + i * 140 : 350;
        return (
          <g key={step.learningPathItemId}>
            {takingLight && (
              <ellipse
                cx={at(i).x}
                cy={WATER_Y - 10}
                rx="78"
                ry="52"
                fill="url(#path-light)"
                className="motion-safe:animate-bloom"
                style={{ animationDelay: `${delay}ms`, transformOrigin: `${at(i).x}px ${WATER_Y - 10}px` }}
              />
            )}
            <Book at={at(i)} scale={SCALE} state={state} number={index + 1} />
            {takingLight && index === lit && (
              // Its waiting cover, fading as the light arrives.
              <g className="animate-book-unlit" style={{ animationDelay: `${delay}ms` }}>
                <Book at={at(i)} scale={SCALE} state="later" number={index + 1} />
              </g>
            )}
          </g>
        );
      })}

      {buddyIndex !== null && (
        <g transform={`translate(${xOf(buddyIndex) + 14} ${WATER_Y - 96})`}>
          <g
            className={hopFrom !== null ? "motion-safe:animate-hop" : undefined}
            style={
              hopFrom !== null
                ? { ["--hop-x" as string]: `${hopFrom - xOf(buddyIndex)}px`, animationDelay: "650ms" }
                : undefined
            }
          >
            <LearningBuddy mood={next >= 0 ? "happy" : "cheer"} size={84} />
          </g>
        </g>
      )}
    </svg>
  );
}
