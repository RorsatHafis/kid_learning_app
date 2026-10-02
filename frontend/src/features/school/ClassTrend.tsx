import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { classAccessApi, type ClassTrend, type ClassTrendPoint } from "@/lib/api/schoolAccess";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { standingLabel } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { TALLY, plural } from "./classLines";
import { WorkspaceSection } from "./ClassWorkspace";

const PERIODS = [4, 8, 12] as const;
const card = "rounded-[16px] bg-white ring-1 ring-line";
const dayMonth = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" });

/**
 * The class over time (X16): where the class stood at the end of each week, from the same evidence
 * rule as today's standings, and how many answers it gave. Counts of children-on-skills only - never
 * a child's name, a score or a ranking. A child counts from when they joined the class.
 */
export function ClassTrendSection({
  classId,
  skillHref,
  refreshKey,
}: {
  classId: string;
  skillHref: (skillId: string) => string;
  refreshKey: number;
}) {
  const [weeks, setWeeks] = useState<(typeof PERIODS)[number]>(8);
  const trend = useAsync(() => classAccessApi.trend(classId, weeks), [classId, weeks, refreshKey]);

  return (
    <WorkspaceSection
      id="class-trend"
      title="The class over time"
      description="Where the class stood at the end of each week on its skills, worked out from the answers given by then - the same rule as the standings above. A child counts from the day they joined the class."
    >
      <div className={cn(card, "flex flex-col gap-6 p-5")}>
        <div role="group" aria-label="Period" className="flex w-fit gap-1 rounded-[12px] bg-paper p-1">
          {PERIODS.map((p) => (
            <button
              key={p}
              type="button"
              aria-pressed={weeks === p}
              onClick={() => setWeeks(p)}
              className={cn(
                "h-9 rounded-[9px] px-3.5 text-sm font-semibold transition-colors duration-150",
                weeks === p
                  ? "bg-white text-tonle-950 shadow-[0_1px_2px_rgba(14,27,58,0.08)] ring-1 ring-line"
                  : "text-ink-soft hover:text-ink",
              )}
            >
              {p} weeks
            </button>
          ))}
        </div>
        {trend.isLoading && !trend.data && <LoadingState label="Loading the class over time…" />}
        {trend.error && <ErrorState error={trend.error} onRetry={trend.refetch} />}
        {trend.data && <Trend data={trend.data} skillHref={skillHref} />}
      </div>
    </WorkspaceSection>
  );
}

const total = (p: ClassTrendPoint) => TALLY.reduce((sum, t) => sum + p[t.key], 0);

function Trend({ data, skillHref }: { data: ClassTrend; skillHref: (skillId: string) => string }) {
  const [active, setActive] = useState<number | null>(null);
  const last = data.points.length - 1;
  const shown = data.points[active ?? last];
  const answers = data.answers.reduce((sum, w) => sum + w.answers, 0);
  const maxAnswers = Math.max(1, ...data.answers.map((w) => w.answers));
  const m = data.movement;
  const moved = [
    m.becameSecure > 0 && `${plural(m.becameSecure, "standing", "standings")} became secure`,
    m.movedUp > 0 && `${m.movedUp} moved up`,
    m.firstReading > 0 && `${m.firstReading} got a first reading`,
    m.movedDown > 0 && `${m.movedDown} moved down`,
  ].filter(Boolean);
  const up = data.bySkill.filter((s) => s.becameSecure + s.movedUp > 0).slice(0, 5);
  const down = data.bySkill
    .filter((s) => s.movedDown > 0)
    .sort((a, b) => b.movedDown - a.movedDown)
    .slice(0, 5);

  const firstWithChildren = data.points.findIndex((p) => p.children > 0);
  if (firstWithChildren === -1) {
    return <p className="text-sm text-ink-soft">No child was in the class during these weeks.</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <p className="text-[0.9375rem] leading-relaxed text-ink">
        Over the last {data.weeks} weeks: {moved.length > 0 ? `${moved.join(", ")}.` : "no standing changed."}{" "}
        <span className="text-ink-soft">
          {plural(answers, "answer", "answers")} given in the class. Each standing is one child on one skill.
        </span>
      </p>

      <div className="flex flex-col gap-3">
        <ul aria-label="Key" className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-ink-soft">
          {TALLY.map((t) => (
            <li key={t.key} className="inline-flex items-center gap-1.5">
              <span aria-hidden className={cn("inline-block h-2.5 w-2.5 rounded-[2px]", t.bar)} />
              {standingLabel[t.standing]}
            </li>
          ))}
        </ul>
        {/* The columns are for the eye; the table below carries the same numbers for everyone. */}
        <div aria-hidden onMouseLeave={() => setActive(null)} className="flex flex-col gap-1.5">
          <div className="flex h-40 items-stretch gap-1.5 sm:gap-2.5">
            {data.points.map((point, i) => {
              const sum = total(point);
              return (
                <div
                  key={point.at}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => setActive(i)}
                  className={cn(
                    "flex flex-1 flex-col-reverse gap-[2px] rounded-[6px] p-[2px] transition-colors duration-100",
                    i === (active ?? last) ? "bg-tonle-50" : "",
                  )}
                >
                  {sum === 0 ? (
                    <span className="h-1 rounded-[3px] bg-line" />
                  ) : (
                    TALLY.map((t) =>
                      point[t.key] > 0 ? (
                        <span
                          key={t.key}
                          className={cn("block w-full rounded-[3px]", t.bar)}
                          style={{ height: `${(point[t.key] / sum) * 100}%` }}
                        />
                      ) : null,
                    )
                  )}
                </div>
              );
            })}
          </div>
          {/* Answers given in each week, under the week's end. */}
          <div className="flex h-8 items-end gap-1.5 border-t border-line pt-1 sm:gap-2.5">
            <span className="flex-1" />
            {data.answers.map((week) => (
              <span key={week.to} className="flex flex-1 justify-center">
                <span
                  className="block w-full max-w-[14px] rounded-t-[3px] bg-tonle-400"
                  style={{ height: `${Math.max(week.answers > 0 ? 3 : 0, (week.answers / maxAnswers) * 26)}px` }}
                />
              </span>
            ))}
          </div>
          <div className="flex justify-between text-[0.6875rem] text-ink-faint">
            <span>{dayMonth(data.points[0].at)}</span>
            <span>Answers each week</span>
            <span>Now</span>
          </div>
        </div>
        {firstWithChildren > 0 && (
          <p className="text-xs text-ink-soft">
            The weeks before {dayMonth(data.points[firstWithChildren].at)} are empty: no child had joined the class yet.
          </p>
        )}
        <p className="min-h-[2.5rem] text-xs leading-relaxed text-ink">
          <span className="font-semibold">{(active ?? last) === last ? "Now" : `Week ending ${dayMonth(shown.at)}`}</span>
          {" · "}
          {plural(shown.children, "child", "children")}
          {total(shown) > 0 &&
            `: ${TALLY.filter((t) => shown[t.key] > 0)
              .map((t) => `${shown[t.key]} ${standingLabel[t.standing].toLowerCase()}`)
              .join(", ")}`}
          {active === null && <span className="text-ink-faint"> - point at or tap a week to see it.</span>}
        </p>
      </div>

      {(up.length > 0 || down.length > 0) && (
        <div className="grid gap-5 sm:grid-cols-2">
          {up.length > 0 && (
            <SkillList title="Skills that moved forward" icon={<ArrowUpRight size={15} aria-hidden className="text-palm-deep" />}>
              {up.map((s) => (
                <SkillLink key={s.skillId} href={skillHref(s.skillId)} name={s.name}>
                  {[s.becameSecure > 0 && `${s.becameSecure} became secure`, s.movedUp > 0 && `${s.movedUp} moved up`]
                    .filter(Boolean)
                    .join(", ")}
                </SkillLink>
              ))}
            </SkillList>
          )}
          {down.length > 0 && (
            <SkillList title="Skills that slipped" icon={<ArrowDownRight size={15} aria-hidden className="text-light-deep" />}>
              {down.map((s) => (
                <SkillLink key={s.skillId} href={skillHref(s.skillId)} name={s.name}>
                  {s.movedDown} moved down
                </SkillLink>
              ))}
            </SkillList>
          )}
        </div>
      )}

      <details className="group">
        <summary className="w-fit cursor-pointer text-sm font-semibold text-tonle-700 hover:text-tonle-900">
          Show as a table
        </summary>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[36rem] text-sm">
            <caption className="sr-only">Where the class stood at the end of each week, and its answers that week</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs font-semibold text-ink-soft">
                <th scope="col" className="py-2 pr-3">
                  Week ending
                </th>
                <th scope="col" className="px-2 py-2 text-right">
                  Children
                </th>
                {TALLY.map((t) => (
                  <th key={t.key} scope="col" className="px-2 py-2 text-right">
                    {standingLabel[t.standing]}
                  </th>
                ))}
                <th scope="col" className="py-2 pl-2 text-right">
                  Answers
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.points.map((point, i) => (
                <tr key={point.at}>
                  <th scope="row" className="py-2 pr-3 text-left font-medium text-ink">
                    {i === last ? "Now" : dayMonth(point.at)}
                  </th>
                  <td className="tabular px-2 py-2 text-right">{point.children}</td>
                  {TALLY.map((t) => (
                    <td key={t.key} className="tabular px-2 py-2 text-right">
                      {point[t.key]}
                    </td>
                  ))}
                  <td className="tabular py-2 pl-2 text-right">{i === 0 ? "-" : data.answers[i - 1].answers}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}

function SkillList({ title, icon, children }: { title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="inline-flex items-center gap-1.5 text-sm font-semibold text-tonle-950">
        {icon}
        {title}
      </h3>
      <ul className="flex flex-col divide-y divide-line border-y border-line">{children}</ul>
    </div>
  );
}

function SkillLink({ href, name, children }: { href: string; name: string; children: React.ReactNode }) {
  return (
    <li>
      <Link to={href} className="group flex flex-col gap-0.5 py-2 text-sm">
        <span className="font-medium text-tonle-800 group-hover:underline group-hover:underline-offset-4">{name}</span>
        <span className="text-xs text-ink-soft">{children}</span>
      </Link>
    </li>
  );
}
