import type { MouseEvent, ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { handOver, plainClick } from "./handOver";

type Kind = NonNullable<Parameters<typeof handOver>[0]>;

/**
 * EXP-1 / WORLD-1: a link whose navigation is shown as a change of place (handOver: the page-wide `kind`, styled in
 * index.css; `preload` fetches the destination's code first). An ordinary link without support, with reduced motion
 * or a new-tab click.
 */
export function TransitionLink({
  to,
  kind,
  preload,
  className,
  children,
  ...rest
}: {
  to: string;
  kind: Kind;
  preload?: () => Promise<unknown>;
  className?: string;
  children: ReactNode;
  "aria-label"?: string;
  title?: string;
}) {
  const navigate = useNavigate();
  function go(event: MouseEvent<HTMLAnchorElement>) {
    if (plainClick(event) && handOver(kind, () => navigate(to), preload)) event.preventDefault();
  }
  return (
    <Link to={to} onClick={go} className={className} {...rest}>
      {children}
    </Link>
  );
}
