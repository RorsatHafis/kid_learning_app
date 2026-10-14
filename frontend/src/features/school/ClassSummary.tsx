import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import type { ClassOverview } from "@/lib/api/schoolAccess";
import { StandingMark } from "@/features/learning/Standing";
import { cn } from "@/lib/utils/cn";
import { classLines } from "./classLines";
import { useMessages } from "@/i18n/catalog";
import { schoolMessages } from "./messages";

/** One class on a staff home: what it is, who is in it, and what needs attention. */
export function ClassSummary({
  overview,
  to,
  showSchool = false,
  teachers,
}: {
  overview: ClassOverview;
  to: string;
  showSchool?: boolean;
  /** The principal's view names the teachers; a teacher's view doesn't need to. */
  teachers?: string[];
}) {
  const m = useMessages(schoolMessages).summary;
  const lines = overview.archived ? [{ tone: "calm" as const, text: m.archived }] : classLines(overview);
  const facts = [
    m.children(overview.children),
    overview.curricula.map((curriculum) => curriculum.name).join(", ") || null,
    teachers === undefined ? null : teachers.length === 0 ? m.noTeacher : teachers.join(", "),
  ].filter((fact): fact is string => fact !== null);

  return (
    <Link
      to={to}
      className="group flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-tonle-50/60 focus-visible:bg-tonle-50/60"
    >
      <div className="flex min-w-0 flex-col gap-1">
        <p className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-semibold text-ink">{overview.className}</span>
          {overview.archived && (
            <span className="rounded-full bg-paper px-2 py-0.5 text-xs font-semibold text-ink-soft ring-1 ring-line-strong">
              {m.archivedBadge}
            </span>
          )}
          {showSchool && <span className="text-sm text-ink-faint">{overview.schoolName}</span>}
        </p>
        <p className="text-sm text-ink-soft [overflow-wrap:anywhere]">{facts.join(" · ")}</p>
        <ul className="mt-0.5 flex flex-col gap-0.5">
          {lines.map((line) => (
            <li
              key={line.text}
              className={cn(
                "flex items-center gap-2 text-sm",
                line.tone === "attention" && "font-medium text-light-deep",
                line.tone === "setup" && "text-light-deep",
                line.tone === "calm" && "text-ink-soft",
              )}
            >
              {line.tone === "attention" && <StandingMark standing="NEEDS_PRACTICE" />}
              {line.text}
            </li>
          ))}
        </ul>
      </div>
      <ChevronRight size={18} className="shrink-0 text-ink-faint transition-transform group-hover:translate-x-0.5" aria-hidden />
    </Link>
  );
}
