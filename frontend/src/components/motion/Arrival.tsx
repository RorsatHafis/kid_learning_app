import { createContext, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";

/**
 * A page arriving: when the address changes, the area that changed eases in (opacity and a small rise, 220 ms, the
 * product's "out" curve) - orientation, so a click visibly lands somewhere - without remounting anything, so a page's
 * state, fetches and unsaved guards are untouched. Nothing moves on first load (the page's own loading state covers
 * that) or for anyone who asks for reduced motion.
 *
 * Boundaries nest: a layout inside a layout (the Studio's tabs inside a workspace) claims the addresses under its
 * {@code scope}, and the outer boundary then stays still while only the inner one moves - switching a tab animates the
 * tab's content, not the whole page.
 */
const ScopeContext = createContext<((scope: string | null) => void) | null>(null);

const KEYFRAMES: Keyframe[] = [
  { opacity: 0, transform: "translateY(6px)" },
  { opacity: 1, transform: "none" },
];
const TIMING: KeyframeAnimationOptions = { duration: 220, easing: "cubic-bezier(0.23, 1, 0.32, 1)" };

function reducedMotion() {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function Arrival({ scope, className, children }: { scope?: string; className?: string; children: ReactNode }) {
  const { pathname } = useLocation();
  const claimOuter = useContext(ScopeContext);
  const [inner, setInner] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const first = useRef(true);

  // This boundary's claim on the outer one, for as long as it is mounted.
  useLayoutEffect(() => {
    if (!scope || !claimOuter) return;
    claimOuter(scope);
    return () => claimOuter(null);
  }, [scope, claimOuter]);

  const key = inner && pathname.startsWith(inner) ? inner : pathname;
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const el = ref.current;
    if (!el || typeof el.animate !== "function" || reducedMotion()) return;
    el.animate(KEYFRAMES, TIMING);
  }, [key]);

  return (
    <ScopeContext.Provider value={setInner}>
      <div ref={ref} className={className}>
        {children}
      </div>
    </ScopeContext.Provider>
  );
}
