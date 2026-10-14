import { Link } from "react-router-dom";
import { AlertTriangle } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolLearningApi, type SchoolClassLearning } from "@/lib/api/schoolAccess";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { standingLabel } from "@/features/learning/explain";
import { TALLY, plural } from "@/features/school/classLines";
import { cn } from "@/lib/utils/cn";

/**
 * Learning across the school (X21): the principal's comparison of classes - who answered this week,
 * where each class's children stand on what it teaches, and what moved in the last weeks. Each row
 * opens the class. Counts only; the same reading each class's own trend gives its teachers.
 */
export function SchoolLearningSection({ schoolId }: { schoolId: string }) {
  const learning = useAsync(() => schoolLearningApi.get(schoolId), [schoolId]);
  const data = learning.data;
  return (
    <section aria-labelledby="school-learning" className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id="school-learning" className="text-lg font-semibold text-tonle-950">
          Learning across the school
        </h2>
        <p className="text-sm text-ink-soft">
          {data ? `The last ${data.weeks} weeks, class by class: ` : ""}who answered this week, where each class&apos;s children
          stand on what it teaches, and what moved.
        </p>
      </div>
      {learning.isLoading && !data && <LoadingState label="Loading learning across the school…" />}
      {learning.error && <ErrorState error={learning.error} onRetry={learning.refetch} />}
      {data && data.classes.length === 0 && (
        <p className="rounded-[14px] border border-dashed border-line-strong px-5 py-4 text-sm text-ink-soft">
          No classes yet. Learning appears here once children join a class.
        </p>
      )}
      {data && data.classes.length > 0 && (
        <div className="overflow-hidden rounded-[14px] border border-line bg-white">
          <ul aria-label="Key" className="flex flex-wrap gap-x-4 gap-y-1.5 border-b border-line px-5 py-3 text-xs text-ink-soft">
            {TALLY.map((t) => (
              <li key={t.key} className="inline-flex items-center gap-1.5">
                <span aria-hidden className={cn("inline-block h-2.5 w-2.5 rounded-[2px]", t.bar)} />
                {standingLabel(t.standing)}
              </li>
            ))}
          </ul>
          <ul className="flex flex-col divide-y divide-line">
            {data.classes.map((row) => (
              <li key={row.classId}>
                <ClassRow row={row} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function ClassRow({ row }: { row: SchoolClassLearning }) {
  const total = TALLY.reduce((sum, t) => sum + row.now[t.key], 0);
  const m = row.movement;
  const moved = [
    m.becameSecure > 0 && `${m.becameSecure} became secure`,
    m.movedUp > 0 && `${m.movedUp} moved up`,
    m.movedDown > 0 && `${m.movedDown} slipped`,
  ].filter(Boolean);
  const silent = row.children > 0 && row.answeringThisWeek === 0;
  const words = TALLY.filter((t) => row.now[t.key] > 0)
    .map((t) => `${row.now[t.key]} ${standingLabel(t.standing).toLowerCase()}`)
    .join(", ");
  return (
    <Link
      to={`/principal/classes/${row.classId}`}
      className="group grid gap-2 px-5 py-4 transition-colors hover:bg-tonle-50/60 focus-visible:bg-tonle-50/60 md:grid-cols-[minmax(0,10rem)_minmax(0,11rem)_minmax(0,1fr)] md:items-center md:gap-6"
    >
      <span className="font-semibold text-ink group-hover:underline group-hover:underline-offset-4">{row.className}</span>
      {row.children === 0 ? (
        <span className="text-sm text-ink-faint md:col-span-2">No children yet</span>
      ) : (
        <>
          <span className={cn("flex items-center gap-1.5 text-sm", silent ? "font-medium text-light-deep" : "text-ink")}>
            {silent && <AlertTriangle size={14} aria-hidden className="shrink-0" />}
            {row.answeringThisWeek} of {plural(row.children, "child", "children")} answered this week
          </span>
          <span className="flex min-w-0 flex-col gap-1">
            {total > 0 && row.skills > 0 ? (
              <>
                <span aria-hidden className="flex h-2.5 gap-[2px] overflow-hidden rounded-full">
                  {TALLY.map((t) =>
                    row.now[t.key] > 0 ? (
                      <span key={t.key} className={t.bar} style={{ width: `${(row.now[t.key] / total) * 100}%` }} />
                    ) : null,
                  )}
                </span>
                <span className="sr-only">Now: {words}.</span>
              </>
            ) : (
              <span className="text-sm text-ink-faint">Nothing assigned to learn yet</span>
            )}
            <span className="text-xs text-ink-soft">
              {moved.length > 0 ? `Last 4 weeks: ${moved.join(" · ")}` : "No standing changed in 4 weeks"}
            </span>
          </span>
        </>
      )}
    </Link>
  );
}
