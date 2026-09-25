import type { ReactNode } from "react";
import { Pill } from "./Pill";

export function SectionHeading({
  eyebrow,
  eyebrowTone = "blue",
  khmerTitle,
  title,
  description,
}: {
  eyebrow: string;
  eyebrowTone?: "blue" | "green" | "purple" | "amber" | "red" | "neutral";
  khmerTitle: string;
  title: ReactNode;
  description?: string;
}) {
  return (
    <div className="mx-auto flex max-w-3xl flex-col items-center gap-5 text-center">
      <Pill tone={eyebrowTone}>{eyebrow}</Pill>
      <p lang="km" className="text-2xl font-bold text-ink sm:text-[32px]">
        {khmerTitle}
      </p>
      <h2 className="text-3xl font-extrabold text-ink sm:text-4xl">{title}</h2>
      {description && <p className="text-base leading-relaxed text-muted">{description}</p>}
    </div>
  );
}
