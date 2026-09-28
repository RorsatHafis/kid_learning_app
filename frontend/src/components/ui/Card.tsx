import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

/** A sheet of paper on the page: use only where grouping genuinely helps. */
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-paper bg-white p-6 shadow-paper ring-1 ring-line/70", className)} {...props} />;
}
