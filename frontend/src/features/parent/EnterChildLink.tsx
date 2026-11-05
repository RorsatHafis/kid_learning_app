import type { MouseEvent, ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { loadChildArea } from "@/routes/areas";
import { handOver, plainClick } from "@/components/motion/handOver";

/**
 * EXP-1: handing the device to the child - the parent's page steps back and the child's world comes forward
 * (handOver "enter-child"). A modified click (new tab), no browser support or reduced motion: an ordinary link.
 */
export function EnterChildLink({ to, className, children }: { to: string; className?: string; children: ReactNode }) {
  const navigate = useNavigate();
  function enter(event: MouseEvent<HTMLAnchorElement>) {
    if (plainClick(event) && handOver("enter-child", () => navigate(to), loadChildArea)) event.preventDefault();
  }
  return (
    <Link to={to} onClick={enter} className={className}>
      {children}
    </Link>
  );
}
