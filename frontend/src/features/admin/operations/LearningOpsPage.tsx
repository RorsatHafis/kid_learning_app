import { useState } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import { adminInsightsApi, type LearningOperations, type PlacementSource } from "@/lib/api/adminInsights";
import type { BankNeed } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { Panel } from "../studio/shared";
import { useMessages } from "@/i18n/catalog";
import { opsMessages } from "./messages";
import { formatNumber } from "@/i18n/format";

const READINESS_SHOWN = 12;

/**
 * How ready the questions children are given are (D-19): per offered curriculum, the share that explains why after a
 * mistake, offers a hint, is read aloud (pre-readers) and is complete in Khmer - so content work goes where it is short.
 */
function Readiness({ rows }: { rows: NonNullable<LearningOperations["readiness"]> }) {
  const m = useMessages(opsMessages).learning;
  // A platform may offer many curricula: the first few, then all on request (in age-group order either way).
  const [all, setAll] = useState(false);
  const shown = all ? rows : rows.slice(0, READINESS_SHOWN);
  return (
    <Panel title={m.readiness}>
      <div className="flex flex-col gap-3">
        <p className="text-sm text-ink-soft">{m.readinessLead}</p>
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {shown.map((row) => (
            <li key={row.curriculumId} className="flex flex-col gap-1.5 py-3 text-sm">
              <span className="font-medium text-ink">
                {row.curriculum} <span className="font-normal text-ink-soft">· {row.ageGroup}</span>
              </span>
              <span className="text-xs text-ink-soft">{m.readinessQuestions(row.questions)}</span>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
                {(
                  [
                    [m.readinessExplained, row.explained, row.questions, "EXPLANATION"],
                    [m.readinessHinted, row.hinted, row.questions, "HINT"],
                    [m.readinessReadAloud, row.readAloud, row.questions, "READ_ALOUD"],
                    [m.readinessKhmer, row.khmerComplete, row.khmerTranslatable, "KHMER"],
                  ] as const satisfies readonly (readonly [string, number, number, BankNeed])[]
                ).map(([label, count, of, need]) => (
                  <div key={label}>
                    <dt className="text-ink-soft">{label}</dt>
                    <dd className={count < of ? "font-semibold text-light-deep" : "font-semibold text-palm-deep"}>
                      {count < of ? (
                        // A gap leads to where it is filled: the subject's bank, showing what still needs it.
                        <Link
                          to={`/admin/studio/${row.subjectId}/questions?needs=${need}`}
                          aria-label={m.readinessFind(label, m.readinessCount(count, of))}
                          className="underline decoration-light/60 underline-offset-2 hover:decoration-light-deep"
                        >
                          {m.readinessCount(count, of)}
                        </Link>
                      ) : (
                        m.readinessCount(count, of)
                      )}
                    </dd>
                  </div>
                ))}
              </dl>
            </li>
          ))}
        </ul>
        {rows.length > READINESS_SHOWN && (
          <button
            type="button"
            onClick={() => setAll(!all)}
            className="w-fit text-sm font-semibold text-tonle-700 hover:text-tonle-900"
            aria-expanded={all}
          >
            {all ? m.readinessFewer : m.readinessAll(rows.length)}
          </button>
        )}
      </div>
    </Panel>
  );
}

const SOURCES: PlacementSource[] = ["CURRICULUM", "ADAPTIVE", "REVIEW", "TEACHER_ASSIGNED"];

const n = (value: number) => formatNumber(value);

/**
 * Learning operations (X15): is the learning loop running, and where does content leave children with
 * nothing to practise? Each problem names what to fix and where. Counts only.
 */
export function LearningOpsPage() {
  const ops = useAsync(() => adminInsightsApi.learningOperations(), []);
  const m = useMessages(opsMessages).learning;
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={m.title} description={m.lead} />
      {ops.isLoading && !ops.data && <LoadingState label={m.loading} />}
      {ops.error && <ErrorState error={ops.error} onRetry={ops.refetch} />}
      {ops.data && <Body data={ops.data} />}
    </div>
  );
}

function Body({ data }: { data: LearningOperations }) {
  const w = data.lastSevenDays;
  const m = useMessages(opsMessages).learning;
  const accuracy = w.answers > 0 ? Math.round((w.answersCorrect / w.answers) * 100) : null;
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_24rem] xl:items-start">
      <div className="flex flex-col gap-6">
        <Panel title={m.lastSevenDays}>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-4">
            <Figure label={m.childrenLearning} value={n(w.childrenLearning)} />
            <Figure label={m.activitiesFinished} value={n(w.attemptsCompleted)} note={m.ofStarted(n(w.attemptsStarted))} />
            <Figure label={m.answers} value={n(w.answers)} note={accuracy === null ? undefined : m.correctShare(accuracy)} />
            <Figure
              label={m.leftMid}
              value={n(w.attemptsLeftOpen)}
              note={m.openOverDay}
              tone={w.attemptsLeftOpen > 0 ? "warn" : undefined}
            />
          </dl>
        </Panel>

        <Panel title={m.placed}>
          <ul className="flex flex-col gap-2.5">
            {SOURCES.map((source) => {
              const value = w.placementsBySource[source] ?? 0;
              const max = Math.max(1, ...Object.values(w.placementsBySource));
              return (
                <li key={source} className="grid grid-cols-[minmax(0,13rem)_minmax(0,1fr)_3.5rem] items-center gap-3 text-sm">
                  <span className="text-ink">{m.source[source]}</span>
                  <span aria-hidden className="h-2.5 overflow-hidden rounded-full bg-paper">
                    <span className="block h-full rounded-full bg-tonle-600" style={{ width: `${(value / max) * 100}%` }} />
                  </span>
                  <span className="tabular text-right font-semibold text-ink">{n(value)}</span>
                </li>
              );
            })}
          </ul>
          <p className="mt-3 text-xs text-ink-soft">
            {m.review(n(w.reviewItemsActive), n(w.reviewDueNow))}
          </p>
        </Panel>
      </div>

      <div className="flex flex-col gap-6">
        <Panel title={m.gaps}>
          <div className="flex flex-col gap-4">
            {data.ageGroupsWithNothingOffered.length > 0 && (
              <p className="flex items-start gap-2 text-sm font-medium text-light-deep">
                <AlertTriangle size={15} aria-hidden className="mt-0.5 shrink-0" />
                {m.nothingOffered(data.ageGroupsWithNothingOffered.join(", "))}
              </p>
            )}
            {data.coverageGaps.length === 0 ? (
              <p className="flex items-start gap-2 text-sm text-palm-deep">
                <CheckCircle2 size={15} aria-hidden className="mt-0.5 shrink-0" />
                {m.allCovered}
              </p>
            ) : (
              <>
                <p className="text-sm text-ink-soft">{m.gapsLead}</p>
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
                {data.coverageGapTotal > data.coverageGaps.length && (
                  <p className="text-sm text-ink-soft">
                    {m.showingFirst(data.coverageGaps.length, data.coverageGapTotal)}
                  </p>
                )}
                <Link to="/admin/studio" className="text-sm font-semibold text-tonle-700 hover:text-tonle-900">
                  {m.openStudio}
                </Link>
              </>
            )}
          </div>
        </Panel>
        {data.readiness && data.readiness.length > 0 && <Readiness rows={data.readiness} />}
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
