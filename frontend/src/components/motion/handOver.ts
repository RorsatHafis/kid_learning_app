import { flushSync } from "react-dom";

/**
 * EXP-1: moving between the grown-up pages and the child's space is a change of place, shown as one - a view
 * transition on the whole page, styled in index.css under html[data-transition=<kind>]. The destination area's code
 * is fetched first (at most 600 ms) so the place, not a loading screen, is what arrives. Returns false when it
 * doesn't run (no browser support, or reduced motion): the caller then navigates as usual.
 */
export function handOver(kind: "enter-child" | "leave-child", go: () => void, preload: () => Promise<unknown>): boolean {
  const reduce = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (typeof document.startViewTransition !== "function" || reduce) return false;
  const root = document.documentElement;
  root.dataset.transition = kind;
  const ready = Promise.race([preload().catch(() => undefined), new Promise((resolve) => setTimeout(resolve, 600))]);
  const transition = document.startViewTransition(async () => {
    await ready;
    flushSync(go);
  });
  void transition.finished.finally(() => {
    if (root.dataset.transition === kind) delete root.dataset.transition;
  });
  return true;
}
