import { useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./Button";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

/**
 * Previous / next through a server-paged list, with where the reader is ("26–50 of 312").
 * Draws nothing when everything fits on one page. Turning a page brings the start of the list
 * (the pager's parent) back into view, as a new page would.
 */
export function Pager({
  page,
  size,
  total,
  onPage,
  label,
  className,
}: {
  /** Zero-based, as the API counts. */
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
  /** What is being paged, for the navigation's name: "Questions". */
  label: string;
  className?: string;
}) {
  const ref = useRef<HTMLElement>(null);
  const m = useMessages(common);
  if (total <= size) return null;

  function turn(next: number) {
    onPage(next);
    const list = ref.current?.parentElement;
    // Smooth unless the reader prefers reduced motion (index.css); clear of the sticky bars.
    // (Optional call: every browser has it; jsdom, which the unit tests run in, does not.)
    if (list && list.getBoundingClientRect().top < 80) list.scrollIntoView?.({ block: "start" });
  }
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <nav ref={ref} aria-label={m.pagesOf(label)} className={cn("flex flex-wrap items-center justify-between gap-3", className)}>
      <p className="tabular text-sm text-ink-soft" aria-live="polite">
        {m.range(from, to, total)}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => turn(page - 1)}>
          <ChevronLeft size={15} aria-hidden /> {m.previous}
        </Button>
        <Button variant="outline" size="sm" disabled={to >= total} onClick={() => turn(page + 1)}>
          {m.next} <ChevronRight size={15} aria-hidden />
        </Button>
      </div>
    </nav>
  );
}

/**
 * How many rows a list has or how many match its filters, and a way to clear the filters.
 * `counted` is the count in words, from the caller's catalog: "3 questions".
 */
export function ListCount({
  total,
  counted,
  filtered,
  onClear,
}: {
  total: number;
  counted: string;
  filtered: boolean;
  onClear: () => void;
}) {
  const m = useMessages(common);
  return (
    <div className="flex min-h-9 flex-wrap items-center justify-between gap-2">
      <p className="tabular text-sm text-ink-soft" aria-live="polite">
        {filtered ? m.matching(counted, total) : counted}
      </p>
      {filtered && (
        <Button variant="ghost" size="sm" onClick={onClear}>
          {m.clearFilters}
        </Button>
      )}
    </div>
  );
}
