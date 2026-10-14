import { useState } from "react";
import { Download } from "lucide-react";
import { adminInsightsApi, type WeekReport } from "@/lib/api/adminInsights";
import { Button } from "@/components/ui/Button";
import { downloadFile, toCsv } from "@/lib/utils/download";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { cn } from "@/lib/utils/cn";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";
import { formatDate, formatNumber } from "@/i18n/format";

type Measure = Exclude<keyof WeekReport, "weekOf">;

const MEASURE_KEYS: Measure[] = ["childrenLearning", "activitiesCompleted", "answers", "newFamilies", "newSchools"];
type MeasureWords = { key: Measure; title: string; unit: string; about: string };
const measures = (): MeasureWords[] => {
  const m = messagesNow(opsMessages).reports.measures;
  return MEASURE_KEYS.map((key) => ({ key, ...m[key] }));
};

/** The table's figures as CSV, the week as an ISO date (Monday, UTC) so a spreadsheet sorts it. */
function weeklyCsv(weeks: WeekReport[]): string {
  const list = measures();
  return toCsv([
    [messagesNow(opsMessages).reports.csvWeek, ...list.map((m) => m.title)],
    ...weeks.map((w) => [w.weekOf, ...list.map((m) => w[m.key])]),
  ]);
}

/** The week's Monday as a short date (a calendar date, so no time zone shifts it). */
const weekLabel = (iso: string) => formatDate(new Date(`${iso}T12:00:00`), { day: "numeric", month: "short" });

/**
 * Reports (X15): the platform over the last twelve weeks - one small chart per measure, each with
 * its own scale (never two measures on one axis), and the same figures as a table. Counts only.
 */
export function ReportsPage() {
  const weeks = useAsync(() => adminInsightsApi.weekly(), []);
  const m = useMessages(opsMessages).reports;
  const MEASURES = MEASURE_KEYS.map((key) => ({ key, ...m.measures[key] }));
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      {weeks.isLoading && !weeks.data && <LoadingState label={m.loading} />}
      {weeks.error && <ErrorState error={weeks.error} onRetry={weeks.refetch} />}
      {weeks.data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {MEASURES.map((measure) => (
              <WeekChart key={measure.key} weeks={weeks.data!} measure={measure} />
            ))}
          </div>
          <Panel
            title={m.table}
            flush
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  downloadFile(
                    `bookeyond-kid-weekly-${weeks.data![weeks.data!.length - 1]?.weekOf ?? "report"}.csv`,
                    weeklyCsv(weeks.data!),
                    "text/csv;charset=utf-8",
                  )
                }
              >
                <Download size={15} aria-hidden /> {m.downloadCsv}
              </Button>
            }
          >
            <div className="overflow-x-auto" tabIndex={0} role="region" aria-label={m.tableRegion}>
              <table className="w-full min-w-[40rem] text-sm">
                <caption className="sr-only">{m.tableCaption}</caption>
                <thead>
                  <tr className="border-b border-line text-left text-xs font-semibold text-ink-soft">
                    <th scope="col" className="px-5 py-2.5 sm:px-6">
                      {m.weekOf}
                    </th>
                    {MEASURES.map((measure) => (
                      <th key={measure.key} scope="col" className="px-3 py-2.5 text-right">
                        {measure.title}
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
                      {MEASURES.map((measure) => (
                        <td key={measure.key} className="tabular px-3 py-2.5 text-right text-ink">
                          {formatNumber(week[measure.key])}
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
function WeekChart({ weeks, measure }: { weeks: WeekReport[]; measure: MeasureWords }) {
  const [active, setActive] = useState<number | null>(null);
  const m = useMessages(opsMessages).reports;
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
        <span className="font-display text-[1.75rem] font-semibold leading-none text-tonle-950">{formatNumber(latest)}</span>
        <span className="text-xs text-ink-soft">{m.thisWeek(formatNumber(previous))}</span>
      </p>
      <div aria-hidden className="relative">
        {/* The top of the scale, recessive. */}
        <div className="flex items-center gap-2 text-[0.6875rem] text-ink-faint">
          <span className="tabular">{formatNumber(max)}</span>
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
          <span className="text-ink-faint">{m.pointAt}</span>
        ) : (
          <>
            {m.weekOfValue(weekLabel(weeks[active].weekOf))} <span className="font-semibold">{formatNumber(values[active])}</span>{" "}
            {measure.unit}
          </>
        )}
      </p>
    </section>
  );
}
