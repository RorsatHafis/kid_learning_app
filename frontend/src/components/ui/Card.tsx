import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft", className)}
      {...props}
    />
  );
}
