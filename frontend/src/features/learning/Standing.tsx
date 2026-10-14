import type { SkillStanding } from "@/lib/api/types";
import { standingHelp, standingLabel } from "./explain";
import { cn } from "@/lib/utils/cn";
import { useLocale } from "@/i18n/catalog";

/** A quiet marker per standing — always next to its words, never colour alone. */
const standingMark: Record<SkillStanding, { mark: string; text: string }> = {
  NOT_STARTED: { mark: "border border-line-strong bg-white", text: "text-ink-faint" },
  TOO_EARLY_TO_TELL: { mark: "border-2 border-tonle-300 bg-white", text: "text-tonle-700" },
  NEEDS_PRACTICE: { mark: "bg-light", text: "text-light-deep" },
  PROGRESSING: { mark: "bg-tonle-600", text: "text-tonle-700" },
  SECURE: { mark: "bg-palm", text: "text-palm-deep" },
};

/** A skill's standing (parent and school views share it). */
export function Standing({ standing }: { standing: SkillStanding }) {
  const m = standingMark[standing];
  useLocale(); // the words follow the interface language
  return (
    <span className={cn("inline-flex items-center gap-2 text-sm font-semibold", m.text)} title={standingHelp(standing)}>
      <span aria-hidden className={cn("h-3 w-3 rounded-[3px]", m.mark)} />
      {standingLabel(standing)}
    </span>
  );
}

/** The marker alone, for a legend or a tally that names the standing next to it. */
export function StandingMark({ standing, className }: { standing: SkillStanding; className?: string }) {
  return <span aria-hidden className={cn("inline-block h-3 w-3 rounded-[3px]", standingMark[standing].mark, className)} />;
}
