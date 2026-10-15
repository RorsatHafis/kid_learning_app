import { useId, type ReactNode } from "react";
import { cn } from "@/lib/utils/cn";
import { BuddyDrawing } from "./BuddyArt";
import { BuddyKindContext, useBuddyKind, type BuddyKind, type BuddyMood } from "./kinds";

/**
 * The Learning Buddy: an animal friend the child chooses (Momo the otter until they do), who walks
 * the path of books with them. It is an authored, bounded character (CLAUDE.md §19) — never
 * generative, never a chatbot; every line it says is written by the product and chosen from real
 * state. It reacts with moods, never with scores: idle (waiting), happy (the child is where they
 * should be), cheer (a success), encourage (after a mistake — warm, never sad), thinking (loading).
 * All motion stops under prefers-reduced-motion (index.css).
 */
const moodMotion: Record<BuddyMood, string> = {
  idle: "animate-float",
  happy: "animate-float",
  cheer: "animate-cheer",
  encourage: "animate-nudge",
  thinking: "animate-float",
};

export function BuddyKindProvider({ kind, children }: { kind: BuddyKind; children: ReactNode }) {
  return <BuddyKindContext.Provider value={kind}>{children}</BuddyKindContext.Provider>;
}

export function LearningBuddy({
  mood = "idle",
  size = 120,
  className,
  label,
  kind,
}: {
  mood?: BuddyMood;
  size?: number;
  className?: string;
  /** When set, the buddy is announced as an image; otherwise it is decorative
   * (the words beside it carry the meaning). */
  label?: string;
  /** Which buddy; by default the one the child chose. */
  kind?: BuddyKind;
}) {
  const chosen = useBuddyKind();
  const uid = useId().replace(/:/g, "");
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
      <BuddyDrawing kind={kind ?? chosen} mood={mood} uid={uid} />
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
    paper: "bg-white text-ink ring-1 ring-line/70",
    mint: "bg-palm-soft text-ink",
    sun: "bg-light-soft text-ink",
    lavender: "bg-dusk-soft text-ink",
    night: "bg-tonle-900/85 text-white ring-1 ring-white/10",
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
