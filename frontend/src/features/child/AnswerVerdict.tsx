import type { Ref } from "react";
import { ArrowRight } from "lucide-react";
import { Bud } from "@/features/world/ProgressBuds";
import type { ActivityItem } from "@/lib/api/types";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { goButton } from "./chips";
import { childMessages } from "./messages";
import { Explanation } from "./Explanation";
import type { AnswerResult } from "./QuestionView";

// Varied, authored words for a right answer (every one still says so plainly).
const RIGHT_HEADLINES = ["right1", "right2", "right3"] as const;

/**
 * What the server said about an answer, under the question: the verdict as a picture for a child who can't read yet
 * (an open lotus, or a bud), the words, the right answer when it wasn't, the author's explanation, and the way on.
 * Shared by the child's activity and the public demo (M2), so a visitor sees exactly what a child sees.
 */
export function AnswerVerdict({
  item,
  index,
  total,
  result,
  onContinue,
  continueRef,
}: {
  item: ActivityItem;
  index: number;
  total: number;
  result: AnswerResult;
  onContinue: () => void;
  continueRef?: Ref<HTMLButtonElement>;
}) {
  const m = useMessages(childMessages).play;
  const q = useMessages(childMessages).question;
  return (
    <div
      role="status"
      className={cn(
        "flex flex-col gap-5 rounded-b-[32px] px-6 pb-7 pt-6 motion-safe:animate-rise-in sm:px-12",
        result.correct ? "bg-light-soft" : "bg-tonle-50",
      )}
    >
      <div className="flex flex-col items-center gap-5 sm:flex-row">
        {/* Y40: the verdict as a picture, for a child who can't read the words yet. */}
        <span aria-hidden className={cn("flex h-16 w-16 shrink-0 items-center justify-center rounded-full ring-4 ring-white", result.correct ? "bg-white motion-safe:animate-pop-in" : "bg-white/80")}>
          <Bud state={result.correct ? "open" : "current"} size={44} />
        </span>
        <div className="flex-1 text-center sm:text-left">
          <p className={cn("font-display text-[1.875rem] font-bold leading-tight", result.correct ? "text-light-deep" : "text-tonle-800")}>
            {result.correct ? m[RIGHT_HEADLINES[index % RIGHT_HEADLINES.length]] : m.notQuite}
          </p>
          <p className="mt-1 text-lg font-medium text-ink">
            {result.correct
              ? m.lanternLit
              : result.correctAnswer?.matches?.length
                ? m.matchesMixed
                : result.correctAnswer?.text
                  ? m.theAnswerIs(
                      item.questionType === "TRUE_FALSE" ? (result.correctAnswer.text === "true" ? q.true : q.false) : result.correctAnswer.text,
                    )
                  : m.keepGoingLine}
          </p>
        </div>
        <button ref={continueRef} type="button" onClick={onContinue} className={goButton}>
          {index + 1 < total ? m.next : m.finish} <ArrowRight size={22} aria-hidden />
        </button>
      </div>
      {result.explanation && <Explanation text={result.explanation} />}
    </div>
  );
}
