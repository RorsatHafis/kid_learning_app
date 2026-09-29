import { useEffect } from "react";

const PRODUCT = "Bookeyond Kid";

/**
 * Names the browser tab after the page (WCAG 2.4.2), e.g. "Your family · Bookeyond Kid", so
 * tabs, history and screen readers can tell pages apart. Nothing is set until the title is known.
 */
export function useDocumentTitle(title: string | null | undefined) {
  useEffect(() => {
    if (!title) return;
    document.title = `${title} · ${PRODUCT}`;
  }, [title]);
}
