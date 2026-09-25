import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * The Learning Buddy: an authored, bounded, non-generative character (product
 * directive §10, §29; CLAUDE.md §19). It never "talks" on its own — every line it
 * says is written by the product and chosen from real state (a correct answer,
 * a finished activity, the next step on the journey).
 *
 * Moods carry meaning, not decoration:
 *  - idle: waiting with the child (gentle float)
 *  - happy: the child is where they should be (home, arriving)
 *  - cheer: a success just happened (jump, arms up)
 *  - encourage: a mistake just happened — supportive, never sad or disappointed
 *  - thinking: something is loading or being saved
 * All motion is disabled under prefers-reduced-motion (index.css).
 */
export type BuddyMood = "idle" | "happy" | "cheer" | "encourage" | "thinking";

const moodMotion: Record<BuddyMood, string> = {
  idle: "animate-float",
  happy: "animate-float",
  cheer: "animate-cheer",
  encourage: "animate-nudge",
  thinking: "animate-float",
};

export function LearningBuddy({
  mood = "idle",
  size = 120,
  className,
  label,
}: {
  mood?: BuddyMood;
  size?: number;
  className?: string;
  /** When set, the buddy is announced as an image; otherwise it is decorative
   * (the words beside it carry the meaning). */
  label?: string;
}) {
  const armsUp = mood === "cheer";
  // Gradient ids must be unique per instance: several buddies can share a page.
  const uid = useId().replace(/:/g, "");
  const ids = { body: `${uid}-body`, leaf: `${uid}-leaf`, book: `${uid}-book` };
  return (
    <svg
      viewBox="0 0 200 200"
      width={size}
      height={size}
      className={cn("shrink-0 overflow-visible", moodMotion[mood], className)}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      // Restart the mood's one-shot motion each time the mood changes.
      key={mood}
    >
      <defs>
        <radialGradient id={ids.body} cx="40%" cy="30%" r="75%">
          <stop offset="0%" stopColor="#D9F7EA" />
          <stop offset="55%" stopColor="#9BE3C4" />
          <stop offset="100%" stopColor="#5CC49B" />
        </radialGradient>
        <linearGradient id={ids.leaf} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#8FDC7A" />
          <stop offset="100%" stopColor="#3FA35E" />
        </linearGradient>
        <linearGradient id={ids.book} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#FFFDF8" />
          <stop offset="100%" stopColor="#F3E9D8" />
        </linearGradient>
      </defs>

      {/* soft ground shadow */}
      <ellipse cx="100" cy="186" rx="46" ry="7" fill="#3A2A1A" opacity="0.08" />

      {/* sprout */}
      <path d="M100 44 C100 34 101 26 104 18" stroke="#3FA35E" strokeWidth="5" strokeLinecap="round" fill="none" />
      <path d="M104 22 C112 8 132 8 136 16 C128 26 114 28 104 22 Z" fill={`url(#${ids.leaf})`} />
      <path d="M102 26 C92 14 74 16 72 24 C80 32 94 32 102 26 Z" fill={`url(#${ids.leaf})`} opacity="0.9" />

      {/* arms (behind body when down, raised when cheering) */}
      {armsUp ? (
        <>
          <path d="M46 104 C30 90 26 72 32 60" stroke="#5CC49B" strokeWidth="14" strokeLinecap="round" fill="none" />
          <path d="M154 104 C170 90 174 72 168 60" stroke="#5CC49B" strokeWidth="14" strokeLinecap="round" fill="none" />
          <circle cx="32" cy="58" r="3" fill="#FFE7A3" />
          <circle cx="168" cy="58" r="3" fill="#FFE7A3" />
          <path d="M20 44 l4 -10 l4 10 l10 4 l-10 4 l-4 10 l-4 -10 l-10 -4 z" fill="#FFD166" />
          <path d="M176 36 l3 -7 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 z" fill="#FFD166" />
        </>
      ) : null}

      {/* body */}
      <path
        d="M100 42 C146 42 170 76 170 118 C170 160 140 182 100 182 C60 182 30 160 30 118 C30 76 54 42 100 42 Z"
        fill={`url(#${ids.body})`}
      />
      <ellipse cx="100" cy="140" rx="44" ry="34" fill="#E8FBF2" opacity="0.7" />

      {/* eyes */}
      <g className="buddy-blink" style={{ transformOrigin: "100px 104px" }}>
        {mood === "thinking" ? (
          <>
            <ellipse cx="78" cy="104" rx="11" ry="13" fill="#fff" />
            <ellipse cx="122" cy="104" rx="11" ry="13" fill="#fff" />
            <circle cx="81" cy="98" r="6" fill="#1E1A17" />
            <circle cx="125" cy="98" r="6" fill="#1E1A17" />
            <circle cx="83" cy="96" r="2" fill="#fff" />
            <circle cx="127" cy="96" r="2" fill="#fff" />
          </>
        ) : mood === "cheer" ? (
          <>
            <path d="M68 106 Q78 94 88 106" stroke="#1E1A17" strokeWidth="5" strokeLinecap="round" fill="none" />
            <path d="M112 106 Q122 94 132 106" stroke="#1E1A17" strokeWidth="5" strokeLinecap="round" fill="none" />
          </>
        ) : (
          <>
            <ellipse cx="78" cy="104" rx="11" ry="13" fill="#fff" />
            <ellipse cx="122" cy="104" rx="11" ry="13" fill="#fff" />
            <circle cx="79" cy="106" r="6.5" fill="#1E1A17" />
            <circle cx="123" cy="106" r="6.5" fill="#1E1A17" />
            <circle cx="81.5" cy="103" r="2.2" fill="#fff" />
            <circle cx="125.5" cy="103" r="2.2" fill="#fff" />
          </>
        )}
      </g>

      {/* cheeks */}
      <ellipse cx="64" cy="124" rx="9" ry="6" fill="#FFB4A2" opacity="0.7" />
      <ellipse cx="136" cy="124" rx="9" ry="6" fill="#FFB4A2" opacity="0.7" />

      {/* mouth */}
      {mood === "cheer" ? (
        <path d="M86 122 Q100 142 114 122 Z" fill="#A63A30" stroke="#1E1A17" strokeWidth="3" strokeLinejoin="round" />
      ) : mood === "thinking" ? (
        <ellipse cx="104" cy="128" rx="5" ry="4" fill="#1E1A17" />
      ) : mood === "encourage" ? (
        <path d="M88 126 Q100 134 112 126" stroke="#1E1A17" strokeWidth="4" strokeLinecap="round" fill="none" />
      ) : (
        <path d="M86 124 Q100 138 114 124" stroke="#1E1A17" strokeWidth="4" strokeLinecap="round" fill="none" />
      )}

      {/* the little book it carries — or a heart when encouraging */}
      {!armsUp && mood !== "encourage" ? (
        <g>
          <path d="M72 150 C84 144 96 146 100 152 C104 146 116 144 128 150 L128 172 C116 166 104 168 100 174 C96 168 84 166 72 172 Z" fill={`url(#${ids.book})`} stroke="#C9B89C" strokeWidth="2" />
          <path d="M100 152 L100 174" stroke="#C9B89C" strokeWidth="2" />
          <path d="M80 156 L94 156 M80 162 L92 162 M106 156 L120 156 M108 162 L120 162" stroke="#7C63E6" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
          <ellipse cx="68" cy="160" rx="9" ry="8" fill="#5CC49B" />
          <ellipse cx="132" cy="160" rx="9" ry="8" fill="#5CC49B" />
        </g>
      ) : null}
      {mood === "encourage" ? (
        <g>
          <path d="M100 172 C82 160 78 148 88 144 C94 142 98 146 100 150 C102 146 106 142 112 144 C122 148 118 160 100 172 Z" fill="#FF8A80" />
          <ellipse cx="80" cy="156" rx="9" ry="8" fill="#5CC49B" />
          <ellipse cx="120" cy="156" rx="9" ry="8" fill="#5CC49B" />
        </g>
      ) : null}
    </svg>
  );
}

/** The buddy with an authored line of speech. The words are the accessible content. */
export function BuddySays({
  mood,
  children,
  size = 96,
  className,
  tone = "paper",
}: {
  mood: BuddyMood;
  children: ReactNode;
  size?: number;
  className?: string;
  tone?: "paper" | "mint" | "sun" | "lavender";
}) {
  const bubble = {
    paper: "bg-white text-ink",
    mint: "bg-kid-mint text-ink",
    sun: "bg-kid-sun text-ink",
    lavender: "bg-kid-lavender text-ink",
  }[tone];
  return (
    <div className={cn("flex items-end gap-3", className)}>
      <LearningBuddy mood={mood} size={size} />
      <div
        className={cn(
          "relative mb-6 max-w-sm animate-pop-in rounded-[22px] px-5 py-3 text-base font-semibold leading-snug shadow-soft",
          bubble,
        )}
      >
        <span
          aria-hidden
          className={cn("absolute -left-2 bottom-4 h-4 w-4 rotate-45 rounded-sm", bubble.split(" ")[0])}
        />
        <span className="relative">{children}</span>
      </div>
    </div>
  );
}
