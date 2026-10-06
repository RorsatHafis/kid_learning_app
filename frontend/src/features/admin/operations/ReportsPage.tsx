import { useState } from "react";
import { adminInsightsApi, type WeekReport } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { cn } from "@/lib/utils/cn";

type Measure = Exclude<keyof WeekReport, "weekOf">;

const MEASURES: { key: Measure; title: string; unit: string; about: string }[] = [
  {
    key: "childrenLearning",
    title: "Children learning",
    unit: "children",
    about: "Children who finished at least one activity.",
  },
  { key: "activitiesCompleted", title: "Activities finished", unit: "activities", about: "Activities children finished." },
  { key: "answers", title: "Answers", unit: "answers", about: "Every answer recorded as learning evidence." },
  { key: "newFamilies", title: "New families", unit: "families", about: "Families who created an account." },
  { key: "newSchools", title: "New schools", unit: "schools", about: "Schools set up by their principals." },
];

const weekLabel = (iso: string) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, { day: "numeric", month: "short", timeZone: "UTC" });

/**
 * Reports (X15): the platform over the last twelve weeks - one small chart per measure, each with
 * its own scale (never two measures on one axis), and the same figures as a table. Counts only.
 */
export function ReportsPage() {
  const weeks = useAsync(() => adminInsightsApi.weekly(), []);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Reports"
        description="The platform over the last twelve weeks. Weeks start on Monday (UTC); the last one is the week so far."
      />
      {weeks.isLoading && !weeks.data && <LoadingState label="Loading reports…" />}
      {weeks.error && <ErrorState error={weeks.error} onRetry={weeks.refetch} />}
      {weeks.data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {MEASURES.map((m) => (
              <WeekChart key={m.key} weeks={weeks.data!} measure={m} />
            ))}
          </div>
          <Panel title="The same figures as a table" flush>
            <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Weekly figures, as a table">
              <table className="w-full min-w-[40rem] text-sm">
                <caption className="sr-only">Platform figures by week, oldest first</caption>
                <thead>
                  <tr className="border-b border-line text-left text-xs font-semibold text-ink-soft">
                    <th scope="col" className="px-5 py-2.5 sm:px-6">
                      Week of
                    </th>
                    {MEASURES.map((m) => (
                      <th key={m.key} scope="col" className="px-3 py-2.5 text-right">
                        {m.title}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {weeks.data.map((week) => (
                    <tr key={week.weekOf}>
                      <th scope="row" className="px-5 py-2.5 text-left font-medium text-ink sm:px-6">
                        {weekLabel(week.weekOf)}
                      </th>
                      {MEASURES.map((m) => (
                        <td key={m.key} className="tabular px-3 py-2.5 text-right text-ink">
                          {week[m.key].toLocaleString()}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        </>
      )}
    </div>
  );
}

/**
 * One measure, twelve thin bars on its own zero-based scale. The latest week is the headline; each
 * bar's week and value appear on hover or focus. The chart itself is hidden from screen readers -
 * the table below carries the same numbers.
 */
function WeekChart({ weeks, measure }: { weeks: WeekReport[]; measure: (typeof MEASURES)[number] }) {
  const [active, setActive] = useState<number | null>(null);
  const values = weeks.map((w) => w[measure.key]);
  const max = Math.max(...values);
  const latest = values[values.length - 1];
  const previous = values[values.length - 2] ?? 0;
  const shown = active ?? values.length - 1;

  return (
    <section
      aria-labelledby={`chart-${measure.key}`}
      className="flex flex-col gap-3 rounded-[16px] bg-white p-5 ring-1 ring-line"
    >
      <div>
        <h2 id={`chart-${measure.key}`} className="text-sm font-semibold text-tonle-950">
          {measure.title}
        </h2>
        <p className="text-xs text-ink-soft">{measure.about}</p>
      </div>
      <p className="flex items-baseline gap-2">
        <span className="font-display text-[1.75rem] font-semibold leading-none text-tonle-950">{latest.toLocaleString()}</span>
        <span className="text-xs text-ink-soft">this week · {previous.toLocaleString()} the week before</span>
      </p>
      <div aria-hidden className="relative">
        {/* The top of the scale, recessive. */}
        <div className="flex items-center gap-2 text-[0.6875rem] text-ink-faint">
          <span className="tabular">{max.toLocaleString()}</span>
          <span className="h-px flex-1 bg-line" />
        </div>
        <div className="flex h-24 items-end gap-[2px] border-b border-line-strong" onMouseLeave={() => setActive(null)}>
          {values.map((value, i) => (
            <div key={weeks[i].weekOf} className="flex h-full flex-1 items-end justify-center" onMouseEnter={() => setActive(i)}>
              <span
                className={cn(
                  "block w-full max-w-[18px] rounded-t-[4px] transition-colors duration-100",
                  i === shown ? "bg-tonle-700" : "bg-tonle-400",
                )}
                style={{ height: max === 0 ? 0 : `${Math.max(value > 0 ? 3 : 0, (value / max) * 100)}%` }}
              />
            </div>
          ))}
        </div>
        <div className="mt-1.5 flex justify-between text-[0.6875rem] text-ink-faint">
          <span>{weekLabel(weeks[0].weekOf)}</span>
          <span>{weekLabel(weeks[weeks.length - 1].weekOf)}</span>
        </div>
      </div>
      <p className="min-h-[1.25rem] text-xs text-ink">
        {active === null ? (
          <span className="text-ink-faint">Point at or tap a week to see it.</span>
        ) : (
          <>
            Week of {weekLabel(weeks[active].weekOf)}: <span className="font-semibold">{values[active].toLocaleString()}</span>{" "}
            {measure.unit}
          </>
        )}
      </p>
    </section>
  );
}
