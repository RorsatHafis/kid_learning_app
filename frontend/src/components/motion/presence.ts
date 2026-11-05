import { useEffect, useState } from "react";

/** How long a closing surface takes to leave - shorter than it took to arrive, so closing feels immediate. */
export const LEAVE_MS = 150;

function animates() {
  return typeof window.matchMedia === "function" && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * EXP-1: a surface that closes leaves instead of vanishing. While `open` turns false it stays `present` (and
 * `leaving`) for LEAVE_MS so its exit can play, then goes. With reduced motion, or where motion can't be detected
 * (tests), it goes at once.
 */
export function usePresence(open: boolean) {
  const [prevOpen, setPrevOpen] = useState(open);
  const [leaving, setLeaving] = useState(false);
  if (open !== prevOpen) {
    // derived from the change itself, during render, so the closing surface never drops out for a frame
    setPrevOpen(open);
    setLeaving(!open && animates());
  }
  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(() => setLeaving(false), LEAVE_MS);
    return () => clearTimeout(timer);
  }, [leaving]);
  return { present: open || leaving, leaving };
}
