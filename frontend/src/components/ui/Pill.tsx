import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

type Tone = "blue" | "green" | "purple" | "amber" | "red" | "neutral";

const toneClasses: Record<Tone, string> = {
  blue: "bg-brand-blueTint text-brand-blueStrong",
  green: "bg-brand-greenTint text-brand-greenStrong",
  purple: "bg-brand-purpleTint text-brand-purpleStrong",
  amber: "bg-brand-amberTint text-brand-amberStrong",
  red: "bg-brand-redTint text-brand-redStrong",
  neutral: "bg-surface-sand text-muted",
};

export function Pill({ tone = "neutral", children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn("inline-flex items-center rounded-pill px-4 py-2 text-[13px] font-semibold", toneClasses[tone], className)}>
      {children}
    </span>
  );
}
