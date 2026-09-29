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
    <figure className="flex flex-col gap-3 rounded-[16px] bg-paper-sunk p-4 sm:p-6">
      <figcaption className="flex items-center justify-between gap-2 text-[0.8125rem] font-medium text-ink-faint">
        <span className="flex items-center gap-1.5">
          <Eye size={14} aria-hidden /> Child&apos;s view
        </span>
        {index !== undefined && total !== undefined && (
          <span>
            Question {index + 1} of {total}
          </span>
        )}
      </figcaption>
      <QuestionView key={item.questionVersionId} item={item} onAnswer={() => setTried(true)} disabled={false} result={null} headingLevel={3} />
      {tried && (
        <p role="status" className="text-sm text-ink-soft">
          Preview only — in the app the server checks the answer and records it as evidence.
        </p>
      )}
    </figure>
  );
}
