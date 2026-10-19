import { useState } from "react";
import { Eye } from "lucide-react";
import type { ActivityItem } from "@/lib/api/types";
import { QuestionView } from "@/features/child/QuestionView";
import { Explanation } from "@/features/child/Explanation";
import { useMessages } from "@/i18n/catalog";
import { studioMessages } from "./messages";

/**
 * A question exactly as the child's app renders it, from exactly what the server
 * would send the child (no answer key). Answers here are not scored or recorded.
 */
export function ChildPreview({
  item,
  index,
  total,
  explanation,
}: {
  item: ActivityItem;
  index?: number;
  total?: number;
  /** Y41: the draft's explanation, shown as the child gets it once they have answered. */
  explanation?: string | null;
}) {
  const [tried, setTried] = useState(false);
  const m = useMessages(studioMessages).preview;
  return (
    <figure className="flex flex-col gap-4 rounded-[20px] bg-[#FFFBF3] p-4 ring-1 ring-[#EADFCB] sm:p-6">
      <figcaption className="flex items-center justify-between gap-2 text-[0.8125rem] font-medium text-ink-soft">
        <span className="flex items-center gap-1.5">
          <Eye size={14} aria-hidden /> {m.childsView}
        </span>
        {index !== undefined && total !== undefined && (
          <span>{m.questionOf(index + 1, total)}</span>
        )}
      </figcaption>
      {/* The child's exact layout at 70%: an editor needs to see it, not to scroll past it. */}
      <div className="[zoom:0.7]">
        <QuestionView key={item.questionVersionId} item={item} onAnswer={() => setTried(true)} disabled={false} result={null} headingLevel={3} autoFocus={false} />
      </div>
      {tried && (
        <div role="status" className="flex flex-col gap-3">
          <p className="text-sm text-ink-soft">{m.previewOnly}</p>
          {explanation && (
            <>
              <p className="text-sm font-semibold text-ink">{m.afterAnswer}</p>
              <div className="[zoom:0.7]">
                <Explanation text={explanation} />
              </div>
            </>
          )}
        </div>
      )}
    </figure>
  );
}
