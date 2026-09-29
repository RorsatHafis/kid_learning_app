import { useCallback, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import type { Page } from "@/lib/api/types";

/**
 * A server-paged list's search, filters and page, kept in the address: going back to the list
 * returns to the same page of the same search, and the link can be shared. `page` is zero-based
 * (as the API counts); the address shows it from 1 and omits the first page.
 */
export function useUrlFilters() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page") ?? "1") - 1) || 0;

  /** Sets or clears filters. A change of filter starts again from the first page. */
  const update = useCallback(
    (changes: Record<string, string | undefined>, keepPage = false) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [key, value] of Object.entries(changes)) {
            if (value) next.set(key, value);
            else next.delete(key);
          }
          if (!keepPage) next.delete("page");
          return next;
        },
        { replace: true },
      ),
    [setParams],
  );

  const setPage = useCallback((next: number) => update({ page: next === 0 ? undefined : String(next + 1) }, true), [update]);

  return { params, page, update, setPage };
}

/** A page past the end (the list shrank, or an old link): move to the last page there is. */
export function usePageInRange(data: Page<unknown> | null, setPage: (page: number) => void) {
  useEffect(() => {
    if (data && data.items.length === 0 && data.total > 0) setPage(Math.ceil(data.total / data.size) - 1);
  }, [data, setPage]);
}
