import { Link } from "react-router-dom";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { adminInsightsApi, type LearningOperations, type PlacementSource } from "@/lib/api/adminInsights";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";

const SOURCE_LABEL: Record<PlacementSource, string> = {
  CURRICULUM: "From curricula",
  ADAPTIVE: "Added by the adaptive engine",
  REVIEW: "Smart Review",
  TEACHER_ASSIGNED: "Assigned by teachers",
};

const n = (value: number) => value.toLocaleString();

/**
 * Learning operations (X15): is the learning loop running, and where does content leave children with
 * nothing to practise? Each problem names what to fix and where. Counts only.
 */
export function LearningOpsPage() {
  const ops = useAsync(() => adminInsightsApi.learningOperations(), []);
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Learning operations"
        description="Whether the learning loop is running - children answering, the engine placing practice, Smart Review scheduling - and where content leaves children with nothing to practise."
      />
      {ops.isLoading && !ops.data && <LoadingState label="Loading learning operations…" />}
      {ops.error && <ErrorState error={ops.error} onRetry={ops.refetch} />}
      {ops.data && <Body data={ops.data} />}
    </div>
  );
}

function Body({ data }: { data: LearningOperations }) {
  const w = data.lastSevenDays;
  const accuracy = w.answers > 0 ? Math.round((w.answersCorrect / w.answers) * 100) : null;
  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem] xl:items-start">
      <div className="flex flex-col gap-6">
        <Panel title="The last 7 days">
          <dl className="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-4">
            <Figure label="Children learning" value={n(w.childrenLearning)} />
            <Figure label="Activities finished" value={n(w.attemptsCompleted)} note={`of ${n(w.attemptsStarted)} started`} />
            <Figure label="Answers" value={n(w.answers)} note={accuracy === null ? undefined : `${accuracy}% correct`} />
            <Figure
              label="Left mid-activity"
              value={n(w.attemptsLeftOpen)}
              note="open for over a day"
              tone={w.attemptsLeftOpen > 0 ? "warn" : undefined}
            />
          </dl>
        </Panel>

        <Panel title="Practice placed on paths (7 days)">
          <ul className="flex flex-col gap-2.5">
            {(Object.keys(SOURCE_LABEL) as PlacementSource[]).map((source) => {
              const value = w.placementsBySource[source] ?? 0;
              const max = Math.max(1, ...Object.values(w.placementsBySource));
              return (
                <li key={source} className="grid grid-cols-[minmax(0,13rem)_minmax(0,1fr)_3.5rem] items-center gap-3 text-sm">
                  <span className="text-ink">{SOURCE_LABEL[source]}</span>
                  <span aria-hidden className="h-2.5 overflow-hidden rounded-full bg-paper">
                    <span className="block h-full rounded-full bg-tonle-600" style={{ width: `${(value / max) * 100}%` }} />
                  </span>
                  <span className="tabular text-right font-semibold text-ink">{n(value)}</span>
                </li>
              );
            })}
          </ul>
          <p className="mt-3 text-xs text-ink-soft">
            Smart Review: {n(w.reviewItemsActive)} skills under review, {n(w.reviewDueNow)} due now.
          </p>
        </Panel>
      </div>

      <div className="flex flex-col gap-6">
        <Panel title="Content gaps">
          <div className="flex flex-col gap-4">
            {data.ageGroupsWithNothingOffered.length > 0 && (
              <p className="flex items-start gap-2 text-sm font-medium text-light-deep">
                <AlertTriangle size={15} aria-hidden className="mt-0.5 shrink-0" />
                Nothing is offered to families for: {data.ageGroupsWithNothingOffered.join(", ")}.
              </p>
            )}
            {data.coverageGaps.length === 0 ? (
              <p className="flex items-start gap-2 text-sm text-palm-deep">
                <CheckCircle2 size={15} aria-hidden className="mt-0.5 shrink-0" />
                Every skill in an offered curriculum has a published activity that practises it.
              </p>
            ) : (
              <>
                <p className="text-sm text-ink-soft">
                  Offered curricula rely on these skills, but no published activity in use practises them, so children can't be
                  given practice on them.
                </p>
                <ul className="flex flex-col divide-y divide-line border-y border-line">
                  {data.coverageGaps.map((gap) => (
                    <li key={`${gap.skillId}:${gap.curriculum}`} className="flex flex-col gap-0.5 py-2.5 text-sm">
                      <span className="font-medium text-ink">{gap.skill}</span>
                      <span className="text-xs text-ink-soft">
                        {gap.subject} · {gap.curriculum}
                      </span>
                    </li>
                  ))}
                </ul>
                <Link to="/admin/studio" className="text-sm font-semibold text-tonle-700 hover:text-tonle-900">
                  Open the Content Studio
                </Link>
              </>
            )}
          </div>
        </Panel>
      </div>
    </div>
  );
}

function Figure({ label, value, note, tone }: { label: string; value: string; note?: string; tone?: "warn" }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs font-medium text-ink-soft">{label}</dt>
      <dd
        className={
          tone === "warn"
            ? "font-display text-2xl font-semibold text-light-deep"
            : "font-display text-2xl font-semibold text-tonle-950"
        }
      >
        {value}
      </dd>
      {note && <dd className="text-xs text-ink-faint">{note}</dd>}
    </div>
  );
}
