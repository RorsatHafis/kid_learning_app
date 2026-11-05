import { flushSync } from "react-dom";

/**
 * EXP-1: a navigation shown as a change of place - a view transition around it, styled in index.css (WORLD-1's
 * travel on the `world` name; with a `kind`, the whole page under html[data-transition=<kind>], e.g. the hand-over
 * between the grown-up pages and the child's space). With `preload`, the destination's code is fetched first (at most
 * 600 ms) so the place, not a loading screen, is what arrives. Returns false when it doesn't run (no browser support,
 * or reduced motion): the caller then navigates as usual.
 */
export function handOver(kind: "enter-child" | "leave-child" | "travel-back" | "step-in" | "step-out" | null, go: () => void, preload?: () => Promise<unknown>): boolean {
  const reduce = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (typeof document.startViewTransition !== "function" || reduce) return false;
  const root = document.documentElement;
  if (kind) root.dataset.transition = kind;
  const ready = preload ? Promise.race([preload().catch(() => undefined), new Promise((resolve) => setTimeout(resolve, 600))]) : null;
  const transition = document.startViewTransition(async () => {
    if (ready) await ready;
    flushSync(go);
  });
  if (kind)
    void transition.finished.finally(() => {
      if (root.dataset.transition === kind) delete root.dataset.transition;
    });
  return true;
}

/** A plain left click - not one asking for a new tab or window, which the browser should handle as a link. */
export function plainClick(event: { metaKey: boolean; ctrlKey: boolean; shiftKey: boolean; button: number }) {
  return !(event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0);
}
