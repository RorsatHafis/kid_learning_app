import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * The Learning Buddy: a firefly who carries light along the child's path. It is an
 * authored, bounded character (CLAUDE.md §19) — never generative, never a chatbot; every
 * line it says is written by the product and chosen from real state.
 *
 * Its lantern is the feedback: it glows gently while waiting, brightens and flares on a
 * success, and stays warm (never dark, never sad) after a mistake. It wears a small krama,
 * the checked scarf of Cambodian families.
 *
 * Moods: idle (waiting), happy (the child is where they should be), cheer (a success),
 * encourage (after a mistake — supportive), thinking (loading or saving).
 * All motion stops under prefers-reduced-motion (index.css).
 */
export type BuddyMood = "idle" | "happy" | "cheer" | "encourage" | "thinking";

const moodMotion: Record<BuddyMood, string> = {
  idle: "animate-float",
  happy: "animate-float",
  cheer: "animate-cheer",
  encourage: "animate-nudge",
  thinking: "animate-float",
};

const glowLevel: Record<BuddyMood, number> = { idle: 0.5, happy: 0.7, cheer: 1, encourage: 0.6, thinking: 0.35 };

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
  const uid = useId().replace(/:/g, "");
  const ids = { glow: `${uid}-glow`, lantern: `${uid}-lantern`, head: `${uid}-head`, wing: `${uid}-wing` };
  const glow = glowLevel[mood];
  const cheer = mood === "cheer";

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
        <radialGradient id={ids.glow}>
          <stop offset="0" stopColor="#FFE9A8" stopOpacity={glow} />
          <stop offset="0.45" stopColor="#FFD27A" stopOpacity={glow * 0.55} />
          <stop offset="1" stopColor="#FFD27A" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={ids.lantern} cx="45%" cy="40%" r="65%">
          <stop offset="0" stopColor="#FFF6D8" />
          <stop offset="0.55" stopColor="#FFD27A" />
          <stop offset="1" stopColor="#F3A83B" />
        </radialGradient>
        <radialGradient id={ids.head} cx="38%" cy="32%" r="75%">
          <stop offset="0" stopColor="#2E4F9E" />
          <stop offset="1" stopColor="#142A60" />
        </radialGradient>
        <linearGradient id={ids.wing} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#E3EAF7" stopOpacity="0.95" />
          <stop offset="1" stopColor="#A2B8E3" stopOpacity="0.6" />
        </linearGradient>
      </defs>

      {/* the light it carries */}
      <circle
        cx="100"
        cy="146"
        r={cheer ? 92 : 74}
        fill={`url(#${ids.glow})`}
        className={mood === "thinking" ? "animate-glow-breathe" : cheer ? "animate-light-up" : undefined}
        style={{ transformOrigin: "100px 146px" }}
      />

      {/* wings */}
      <g className="buddy-wings" style={{ transformOrigin: "100px 104px" }}>
        <path d="M92 100 C70 72 38 70 30 88 C24 104 52 118 90 110 Z" fill={`url(#${ids.wing})`} stroke="#A2B8E3" strokeWidth="2" />
        <path d="M108 100 C130 72 162 70 170 88 C176 104 148 118 110 110 Z" fill={`url(#${ids.wing})`} stroke="#A2B8E3" strokeWidth="2" />
        <path d="M86 104 C66 92 48 92 42 100" stroke="#C9D6EF" strokeWidth="1.5" fill="none" opacity="0.8" />
        <path d="M114 104 C134 92 152 92 158 100" stroke="#C9D6EF" strokeWidth="1.5" fill="none" opacity="0.8" />
      </g>

      {/* lantern body */}
      <path d="M100 116 C124 116 136 134 134 154 C132 176 118 188 100 188 C82 188 68 176 66 154 C64 134 76 116 100 116 Z" fill={`url(#${ids.lantern})`} />
      <g stroke="#C77A1E" strokeWidth="2.2" strokeLinecap="round" opacity="0.35" fill="none">
        <path d="M72 142 C88 148 112 148 128 142" />
        <path d="M70 160 C88 167 112 167 130 160" />
        <path d="M76 176 C90 181 110 181 124 176" />
      </g>

      {/* little arms: up when cheering */}
      {cheer ? (
        <g stroke="#142A60" strokeWidth="7" strokeLinecap="round" fill="none">
          <path d="M70 124 C58 112 54 98 58 86" />
          <path d="M130 124 C142 112 146 98 142 86" />
        </g>
      ) : (
        <g stroke="#142A60" strokeWidth="7" strokeLinecap="round" fill="none">
          <path d="M72 128 C62 134 60 144 64 152" />
          <path d="M128 128 C138 134 140 144 136 152" />
        </g>
      )}

      {/* krama: a small checked scarf */}
      <path d="M64 112 C80 122 120 122 136 112 L138 124 C120 134 80 134 62 124 Z" fill="#F6F7FB" />
      <g fill="#C2412F" opacity="0.9">
        {[68, 80, 92, 104, 116, 128].map((x) => (
          <rect key={x} x={x} y="113" width="6" height="16" rx="1" transform={`rotate(-4 ${x} 120)`} />
        ))}
      </g>
      <path d="M126 122 L134 146 L124 144 L120 124 Z" fill="#C2412F" />

      {/* head */}
      <circle cx="100" cy="82" r="40" fill={`url(#${ids.head})`} />
      {/* antennae with tiny lights */}
      <g stroke="#142A60" strokeWidth="4" strokeLinecap="round" fill="none">
        <path d="M86 46 C82 32 72 24 62 26" />
        <path d="M114 46 C118 32 128 24 138 26" />
      </g>
      <circle cx="60" cy="26" r="6" fill="#FFD27A" />
      <circle cx="140" cy="26" r="6" fill="#FFD27A" />

      {/* face */}
      <g className="buddy-blink" style={{ transformOrigin: "100px 82px" }}>
        {mood === "happy" || cheer ? (
          <g stroke="#FFFFFF" strokeWidth="5" strokeLinecap="round" fill="none">
            <path d="M74 84 Q84 72 94 84" />
            <path d="M106 84 Q116 72 126 84" />
          </g>
        ) : mood === "thinking" ? (
          <>
            <ellipse cx="84" cy="80" rx="11" ry="13" fill="#FFFFFF" />
            <ellipse cx="116" cy="80" rx="11" ry="13" fill="#FFFFFF" />
            <circle cx="86" cy="74" r="6" fill="#0A1533" />
            <circle cx="118" cy="74" r="6" fill="#0A1533" />
          </>
        ) : (
          <>
            <ellipse cx="84" cy="82" rx="11" ry="13" fill="#FFFFFF" />
            <ellipse cx="116" cy="82" rx="11" ry="13" fill="#FFFFFF" />
            <circle cx="85" cy="84" r="6.5" fill="#0A1533" />
            <circle cx="117" cy="84" r="6.5" fill="#0A1533" />
            <circle cx="87.5" cy="81" r="2.3" fill="#FFFFFF" />
            <circle cx="119.5" cy="81" r="2.3" fill="#FFFFFF" />
          </>
        )}
      </g>
      <ellipse cx="70" cy="98" rx="7" ry="4.5" fill="#E48BA0" opacity="0.85" />
      <ellipse cx="130" cy="98" rx="7" ry="4.5" fill="#E48BA0" opacity="0.85" />
      {cheer ? (
        <path d="M88 98 Q100 116 112 98 Z" fill="#E0782F" stroke="#FFFFFF" strokeWidth="2.5" strokeLinejoin="round" />
      ) : mood === "thinking" ? (
        <circle cx="104" cy="102" r="3.5" fill="#FFFFFF" />
      ) : mood === "encourage" ? (
        <path d="M91 101 Q100 107 109 101" stroke="#FFFFFF" strokeWidth="3.5" strokeLinecap="round" fill="none" />
      ) : (
        <path d="M89 99 Q100 111 111 99" stroke="#FFFFFF" strokeWidth="3.5" strokeLinecap="round" fill="none" />
      )}

      {/* a success sends sparks up */}
      {cheer && (
        <g fill="#FFD27A">
          <path d="M28 70 l3 -8 l3 8 l8 3 l-8 3 l-3 8 l-3 -8 l-8 -3 z" />
          <path d="M168 58 l2.5 -6 l2.5 6 l6 2.5 l-6 2.5 l-2.5 6 l-2.5 -6 l-6 -2.5 z" />
          <path d="M176 120 l2 -5 l2 5 l5 2 l-5 2 l-2 5 l-2 -5 l-5 -2 z" />
        </g>
      )}
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
  tone?: "paper" | "mint" | "sun" | "lavender" | "night";
}) {
  const bubble = {
    paper: "bg-white text-ink",
    mint: "bg-palm-soft text-ink",
    sun: "bg-light-soft text-ink",
    lavender: "bg-dusk-soft text-ink",
    night: "bg-tonle-900/80 text-white ring-1 ring-white/10",
  }[tone];
  return (
    <div className={cn("flex items-end gap-3", className)}>
      <LearningBuddy mood={mood} size={size} />
      <div className={cn("relative mb-6 max-w-sm animate-pop-in rounded-[20px] px-5 py-3 text-base font-semibold leading-snug shadow-paper", bubble)}>
        <span aria-hidden className={cn("absolute -left-1.5 bottom-4 h-3.5 w-3.5 rotate-45 rounded-[3px]", bubble.split(" ")[0])} />
        <span className="relative">{children}</span>
      </div>
    </div>
  );
}
