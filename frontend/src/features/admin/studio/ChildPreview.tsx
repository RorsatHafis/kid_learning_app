import { useState } from "react";
import { Eye } from "lucide-react";
import type { ActivityItem } from "@/lib/api/types";
import { QuestionView } from "@/features/child/QuestionView";

/**
 * A question exactly as the child's app renders it, from exactly what the server
 * would send the child (no answer key). Answers here are not scored or recorded.
 */
export function ChildPreview({ item, index, total }: { item: ActivityItem; index?: number; total?: number }) {
  const [tried, setTried] = useState(false);
  return (
    <figure className="flex flex-col gap-4 rounded-[20px] bg-[#FFFBF3] p-4 ring-1 ring-[#EADFCB] sm:p-6">
      <figcaption className="flex items-center justify-between gap-2 text-[0.8125rem] font-medium text-ink-soft">
        <span className="flex items-center gap-1.5">
          <Eye size={14} aria-hidden /> Child&apos;s view
        </span>
        {index !== undefined && total !== undefined && (
          <span>
            Question {index + 1} of {total}
          </span>
        )}
      </figcaption>
      {/* The child's exact layout at 70%: an editor needs to see it, not to scroll past it. */}
      <div className="[zoom:0.7]">
        <QuestionView key={item.questionVersionId} item={item} onAnswer={() => setTried(true)} disabled={false} result={null} headingLevel={3} autoFocus={false} />
      </div>
      {tried && (
        <p role="status" className="text-sm text-ink-soft">
          Preview only — in the app the server checks the answer and records it as evidence.
        </p>
      )}
    </figure>
  );
}
