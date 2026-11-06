import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { cn } from "@/lib/utils/cn";
import { goButton } from "./chips";
import { useMessages } from "@/i18n/catalog";
import { childMessages } from "./messages";

/* The child space's shared waiting and trouble states (home, journey list, player, shelf, buddy page). */
export function Waiting({ line }: { line: string }) {
  return (
    <div role="status" className="flex flex-col items-center gap-4 px-6 pt-28 text-center motion-safe:animate-fade-in-late">
      <LearningBuddy mood="thinking" size={132} />
      <p className="rounded-pill bg-white/90 px-5 py-2 text-lg font-semibold text-tonle-900 shadow-tight">{line}</p>
    </div>
  );
}

/** Something went wrong in the child space. Without `onRetry` there is nothing to try again (e.g. a closed space). */
export function Trouble({ onRetry, compact, line }: { onRetry?: () => void; compact?: boolean; line?: string }) {
  const m = useMessages(childMessages);
  return (
    <div role="alert" className={cn("flex flex-col items-center gap-4 px-6 text-center", compact ? "py-6" : "pt-28")}>
      <LearningBuddy mood="encourage" size={compact ? 88 : 124} />
      <p className="max-w-sm rounded-[20px] bg-white/95 px-5 py-3 text-lg font-semibold text-tonle-950 shadow-tight">{line ?? m.trouble}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className={cn(goButton, "min-h-14 px-9 text-lg")}>
          {m.tryAgain}
        </button>
      )}
    </div>
  );
}
