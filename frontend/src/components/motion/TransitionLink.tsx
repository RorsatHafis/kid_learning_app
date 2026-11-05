import type { MouseEvent, ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { handOver, plainClick } from "./handOver";

type Kind = Parameters<typeof handOver>[0];

/**
 * EXP-1 / WORLD-1: a link whose navigation is shown as a change of place (handOver: the page-wide `kind`, styled in
 * index.css; `preload` fetches the destination's code first; `origin` is where in the scene the travel zooms from or
 * to, in percent - WORLD-1's --travel-origin). An ordinary link without support, with reduced motion or a new-tab click.
 */
export function TransitionLink({
  to,
  kind,
  preload,
  origin,
  className,
  children,
  ...rest
}: {
  to: string;
  kind: Kind;
  preload?: () => Promise<unknown>;
  origin?: { x: number; y: number };
  className?: string;
  children: ReactNode;
  "aria-label"?: string;
  title?: string;
}) {
  const navigate = useNavigate();
  function go(event: MouseEvent<HTMLAnchorElement>) {
    if (!plainClick(event)) return;
    if (origin) document.documentElement.style.setProperty("--travel-origin", `${origin.x}% ${origin.y}%`);
    if (handOver(kind, () => navigate(to), preload)) event.preventDefault();
  }
  return (
    <Link to={to} onClick={go} className={className} {...rest}>
      {children}
    </Link>
  );
}
