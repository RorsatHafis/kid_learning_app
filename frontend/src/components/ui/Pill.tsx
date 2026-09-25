import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

type Tone = "blue" | "green" | "purple" | "amber" | "red" | "neutral";

const toneClasses: Record<Tone, string> = {
  blue: "bg-brand-blueTint text-brand-blue",
  green: "bg-brand-greenTint text-brand-green",
  purple: "bg-brand-purpleTint text-brand-purple",
  amber: "bg-brand-amberTint text-brand-amber",
  red: "bg-brand-redTint text-brand-red",
  neutral: "bg-surface-sand text-muted",
};

export function Pill({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center rounded-pill px-4 py-2 text-[13px] font-semibold", toneClasses[tone], className)}>
      {children}
    </span>
  );
}
