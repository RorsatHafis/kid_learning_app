import type { MouseEvent, ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { handOver, plainClick } from "@/components/motion/handOver";

/**
 * WORLD-1 / EXP-1: the child's own corners (the shelf, the buddy) are places off the path. Going to one steps in -
 * the page steps back and the corner comes forward; coming back steps out - the corner lifts away and the path
 * settles in. One view transition (index.css, data-transition step-in / step-out); an ordinary link without
 * support, with reduced motion or a new-tab click.
 */
export function StepLink({
  to,
  direction,
  className,
  children,
  ...rest
}: {
  to: string;
  direction: "in" | "out";
  className?: string;
  children: ReactNode;
  "aria-label"?: string;
  title?: string;
}) {
  const navigate = useNavigate();
  function step(event: MouseEvent<HTMLAnchorElement>) {
    if (plainClick(event) && handOver(direction === "in" ? "step-in" : "step-out", () => navigate(to))) event.preventDefault();
  }
  return (
    <Link to={to} onClick={step} className={className} {...rest}>
      {children}
    </Link>
  );
}
