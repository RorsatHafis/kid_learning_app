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
import { formatDay } from "@/lib/utils/date";
import { cn } from "@/lib/utils/cn";
import { SectionTitle } from "./SectionTitle";

const standingTone: Record<SkillStanding, string> = {
  NOT_STARTED: "bg-surface-sand text-muted",
  TOO_EARLY_TO_TELL: "bg-kid-sky text-kid-skyDeep",
  NEEDS_PRACTICE: "bg-kid-peach text-kid-peachDeep",
  PROGRESSING: "bg-kid-lavender text-kid-lavenderDeep",
  SECURE: "bg-kid-mint text-kid-mintDeep",
};

/**
 * What the evidence says, skill by skill (directive §13.3). Evidence strength and
 * performance are shown separately: "not started" and "too early to tell" are
 * never presented as weakness, and a claim about performance only appears once
 * there is enough evidence for it (the server's EvidenceStandard).
 */
export function SkillsSection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.skills(childId), [childId]);

  return (
    <section aria-labelledby="skills" className="flex flex-col gap-4">
      <SectionTitle id="skills" title="Skills & evidence" subtitle="What your child's answers show so far, skill by skill." />
      <details className="rounded-2xl bg-white/70 p-4 text-sm text-muted">
        <summary className="cursor-pointer font-semibold text-ink">How we describe progress</summary>
        <div className="mt-3 flex flex-col gap-2">
          <p>
            Every answer is evidence. We only describe how a skill is going once there are at least three scored answers on it —
            before that it is <strong>too early to tell</strong>, not weak.
          </p>
          <ul className="grid gap-1 sm:grid-cols-2">
            {(Object.keys(standingLabel) as SkillStanding[]).map((standing) => (
              <li key={standing}>
                <span className={cn("mr-2 rounded-pill px-2 py-0.5 text-xs font-bold", standingTone[standing])}>
                  {standingLabel[standing]}
                </span>
                {standingHelp[standing]}
              </li>
            ))}
          </ul>
        </div>
      </details>

      {isLoading && <LoadingState label="Loading skills…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <EmptyState title="No skills yet" description="Choose a learning path to see the skills it builds." />
      )}
      {data?.map((curriculum) => (
        <div key={curriculum.enrollmentId} className="flex flex-col gap-3">
          {data.length > 1 && <h3 className="text-base font-bold text-ink">{curriculum.curriculum.name}</h3>}
          {curriculum.objectives.map((objective) => (
            <ObjectiveCard key={objective.objectiveId} objective={objective} />
          ))}
        </div>
      ))}
    </section>
  );
}

function ObjectiveCard({ objective }: { objective: ObjectiveProgress }) {
  return (
    <article className="rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h4 className="text-lg font-bold text-ink">{objective.title}</h4>
          {objective.description && <p className="text-sm text-muted">{objective.description}</p>}
        </div>
        <span
          className={cn(
            "rounded-pill px-3 py-1 text-xs font-bold",
            objective.standing === "SECURE"
              ? "bg-kid-mint text-kid-mintDeep"
              : objective.standing === "IN_PROGRESS"
                ? "bg-kid-lavender text-kid-lavenderDeep"
                : "bg-surface-sand text-muted",
          )}
        >
          {objectiveStandingLabel[objective.standing]}
        </span>
      </div>
      <p className="mt-2 text-sm text-muted">
        {objective.skillsPractised} of {objective.skillsMapped} skills practised · {objective.skillsWithSufficientEvidence} with
        enough evidence to judge
      </p>
      <ul className="mt-4 flex flex-col divide-y divide-hairline">
        {objective.skills.map((skill) => (
          <SkillRow key={skill.skillId} skill={skill} />
        ))}
      </ul>
    </article>
  );
}

function SkillRow({ skill }: { skill: SkillProgress }) {
  return (
    <li className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="font-semibold text-ink">{skill.name}</p>
        <p className="text-sm text-muted">
          {skill.scoredAnswers > 0
            ? `${skill.correctAnswers} of ${skill.scoredAnswers} answers correct${
                skill.lastPracticedAt ? ` · last practised ${formatDay(skill.lastPracticedAt)}` : ""
              }`
            : "No answers yet"}
        </p>
        {skill.inReview && (
          <p className="mt-1 flex items-center gap-1 text-sm font-semibold text-kid-sunDeep">
            <RotateCcw size={14} aria-hidden /> In Smart Review
            {skill.reviewDueAt ? ` · due ${formatDay(skill.reviewDueAt)}` : ""}
          </p>
        )}
      </div>
      <div className="flex items-center gap-3">
        <EvidenceMeter level={skill.evidenceLevel} />
        <span className={cn("rounded-pill px-3 py-1 text-xs font-bold", standingTone[skill.standing])} title={standingHelp[skill.standing]}>
          {standingLabel[skill.standing]}
        </span>
      </div>
    </li>
  );
}

function EvidenceMeter({ level }: { level: EvidenceLevel }) {
  const filled = evidenceDots[level];
  return (
    <span className="flex items-center gap-1.5" title={evidenceLabel[level]}>
      <span className="sr-only">{evidenceLabel[level]}</span>
      {[1, 2, 3].map((dot) => (
        <span key={dot} aria-hidden className={cn("h-2.5 w-2.5 rounded-full", dot <= filled ? "bg-ink" : "bg-hairline")} />
      ))}
    </span>
  );
}
