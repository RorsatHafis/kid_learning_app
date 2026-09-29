import { RotateCcw } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { EvidenceLevel, ObjectiveProgress, SkillProgress, SkillStanding } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import {
  evidenceDots,
  evidenceLabel,
  objectiveStandingLabel,
  standingHelp,
  standingLabel,
} from "@/features/learning/explain";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { formatDay } from "@/lib/utils/date";
import { cn } from "@/lib/utils/cn";
import { SectionTitle } from "./SectionTitle";

/** A quiet marker per standing — always next to its words, never colour alone. */
const standingMark: Record<SkillStanding, { mark: string; text: string }> = {
  NOT_STARTED: { mark: "border border-line-strong bg-white", text: "text-ink-faint" },
  TOO_EARLY_TO_TELL: { mark: "border-2 border-tonle-300 bg-white", text: "text-tonle-700" },
  NEEDS_PRACTICE: { mark: "bg-light", text: "text-light-deep" },
  PROGRESSING: { mark: "bg-tonle-600", text: "text-tonle-700" },
  SECURE: { mark: "bg-palm", text: "text-palm-deep" },
};

function Standing({ standing }: { standing: SkillStanding }) {
  const m = standingMark[standing];
  return (
    <span className={cn("inline-flex items-center gap-2 text-sm font-semibold", m.text)} title={standingHelp[standing]}>
      <span aria-hidden className={cn("h-3 w-3 rounded-[3px]", m.mark)} />
      {standingLabel[standing]}
    </span>
  );
}

/**
 * What the evidence says, skill by skill (directive §13.3). Evidence strength and
 * performance are shown separately: "not started" and "too early to tell" are
 * never presented as weakness, and a claim about performance only appears once
 * there is enough evidence for it (the server's EvidenceStandard).
 */
export function SkillsSection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.skills(childId), [childId]);

  return (
    <section aria-labelledby="skills" className="flex flex-col gap-5">
      <SectionTitle id="skills" title="Skills & evidence" subtitle="What your child's answers show so far, skill by skill." />

      {isLoading && <LoadingState label="Loading skills…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && <EmptyState title="No skills yet" description="Choose a learning path to see the skills it builds." />}
      {data?.map((curriculum) => (
        <div key={curriculum.enrollmentId} className="flex flex-col gap-4">
          {data.length > 1 && <h3 className="font-display text-lg font-semibold text-tonle-950">{curriculum.curriculum.name}</h3>}
          {curriculum.objectives.map((objective) => (
            <ObjectiveTable key={objective.objectiveId} objective={objective} />
          ))}
        </div>
      ))}

      <details className="group rounded-paper bg-white px-6 py-4 ring-1 ring-line/70 open:pb-6">
        <summary className="cursor-pointer list-none text-sm font-semibold text-tonle-700 marker:hidden">
          <span className="inline-flex items-center gap-2">
            How we describe progress
            <span aria-hidden className="text-ink-faint transition-transform duration-200 group-open:rotate-90">›</span>
          </span>
        </summary>
        <div className="mt-4 grid gap-6 text-sm text-ink-soft sm:grid-cols-2">
          <div className="flex flex-col gap-3">
            <p>
              Every answer is evidence. We only describe how a skill is going once there are at least three scored answers on it —
              before that it is <strong className="text-ink">too early to tell</strong>, not weak.
            </p>
            <ul className="flex flex-col gap-2">
              {(Object.keys(evidenceDots) as EvidenceLevel[]).map((level) => (
                <li key={level} className="flex items-center gap-3">
                  <EvidenceLine level={level} />
                  <span>{evidenceLabel[level]}</span>
                </li>
              ))}
            </ul>
          </div>
          <ul className="flex flex-col gap-2.5">
            {(Object.keys(standingLabel) as SkillStanding[]).map((standing) => (
              <li key={standing} className="flex flex-col">
                <Standing standing={standing} />
                <span className="pl-5">{standingHelp[standing]}</span>
              </li>
            ))}
          </ul>
        </div>
      </details>
    </section>
  );
}

function ObjectiveTable({ objective }: { objective: ObjectiveProgress }) {
  return (
    <article className="overflow-hidden rounded-paper bg-white shadow-paper ring-1 ring-line/70">
      <header className="flex flex-wrap items-start justify-between gap-4 px-6 pb-5 pt-6 sm:px-8">
        <div className="max-w-xl">
          <h4 className="font-display text-lg font-semibold text-tonle-950">{objective.title}</h4>
          {objective.description && <p className="mt-1 text-sm text-ink-soft">{objective.description}</p>}
          <p className="mt-2 tabular text-sm text-ink-faint">
            {objective.skillsPractised} of {objective.skillsMapped} skills practised · {objective.skillsWithSufficientEvidence} with enough
            evidence to judge
          </p>
        </div>
        <span
          className={cn(
            "rounded-[8px] px-2.5 py-1 text-[0.8125rem] font-semibold ring-1 ring-inset",
            objective.standing === "SECURE"
              ? "bg-palm-soft text-palm-deep ring-palm/30"
              : objective.standing === "IN_PROGRESS"
                ? "bg-tonle-50 text-tonle-700 ring-tonle-200"
                : "bg-paper text-ink-soft ring-line",
          )}
        >
          {objectiveStandingLabel[objective.standing]}
        </span>
      </header>
      <div role="table" aria-label={`Skills in ${objective.title}`}>
        <div role="row" className="hidden grid-cols-[minmax(0,1fr)_150px_150px] gap-4 border-y border-line bg-paper px-8 py-2.5 text-[0.8125rem] font-semibold text-ink-faint sm:grid">
          <span role="columnheader">Skill</span>
          <span role="columnheader">Evidence</span>
          <span role="columnheader">What it shows</span>
        </div>
        <div role="rowgroup" className="divide-y divide-line border-t border-line sm:border-t-0">
          {objective.skills.map((skill) => (
            <SkillRow key={skill.skillId} skill={skill} />
          ))}
        </div>
      </div>
    </article>
  );
}

function SkillRow({ skill }: { skill: SkillProgress }) {
  return (
    <div role="row" className="grid gap-3 px-6 py-4 sm:grid-cols-[minmax(0,1fr)_150px_150px] sm:items-center sm:gap-4 sm:px-8">
      <div role="cell">
        <p className="font-semibold text-ink">{skill.name}</p>
        <p className="tabular text-sm text-ink-soft">
          {skill.scoredAnswers > 0
            ? `${skill.correctAnswers} of ${skill.scoredAnswers} answers correct${skill.lastPracticedAt ? ` · last practised ${formatDay(skill.lastPracticedAt)}` : ""}`
            : "No answers yet"}
        </p>
        {skill.inReview && (
          <p className="mt-1 flex items-center gap-1.5 text-sm font-semibold text-light-deep">
            <RotateCcw size={14} aria-hidden /> In Smart Review
            {skill.reviewDueAt ? ` · due ${formatDay(skill.reviewDueAt)}` : ""}
          </p>
        )}
      </div>
      <div role="cell" className="flex items-center gap-3">
        <EvidenceLine level={skill.evidenceLevel} />
        <span className="text-[0.8125rem] text-ink-soft sm:sr-only">{evidenceLabel[skill.evidenceLevel]}</span>
      </div>
      <div role="cell">
        <Standing standing={skill.standing} />
      </div>
    </div>
  );
}
