import type { ReactNode } from "react";
import { loadChildArea } from "@/routes/areas";
import { TransitionLink } from "@/components/motion/TransitionLink";

/**
 * EXP-1: handing the device to the child - the parent's page steps back and the child's world comes forward
 * ("enter-child"), the child area's code fetched first so the world, not a loading screen, arrives.
 */
export function EnterChildLink({ to, className, children }: { to: string; className?: string; children: ReactNode }) {
  return (
    <TransitionLink to={to} kind="enter-child" preload={loadChildArea} className={className}>
      {children}
    </TransitionLink>
  );
}
