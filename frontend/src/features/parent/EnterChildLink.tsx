import type { MouseEvent, ReactNode } from "react";
import { flushSync } from "react-dom";
import { Link, useNavigate } from "react-router-dom";
import { loadChildArea } from "@/routes/areas";

/**
 * EXP-1: handing the device to the child is a change of place, so it is shown as one - the parent's page steps back
 * and the child's world comes forward (a view transition, styled in index.css under data-transition="enter-child").
 * The child area's code is fetched first (briefly), so the world, not a loading screen, is what arrives. Without the
 * browser's support, with reduced motion, or a modified click (new tab), it is an ordinary link.
 */
export function EnterChildLink({ to, className, children }: { to: string; className?: string; children: ReactNode }) {
  const navigate = useNavigate();
  function enter(event: MouseEvent<HTMLAnchorElement>) {
    const reduce = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (typeof document.startViewTransition !== "function" || reduce || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return;
    event.preventDefault();
    const root = document.documentElement;
    root.dataset.transition = "enter-child";
    const ready = Promise.race([loadChildArea().catch(() => undefined), new Promise((resolve) => setTimeout(resolve, 600))]);
    const transition = document.startViewTransition(async () => {
      await ready;
      flushSync(() => navigate(to));
    });
    void transition.finished.finally(() => {
      delete root.dataset.transition;
    });
  }
  return (
    <Link to={to} onClick={enter} className={className}>
      {children}
    </Link>
  );
}
