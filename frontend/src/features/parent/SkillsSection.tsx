import { Link } from "react-router-dom";
import { ChevronRight, RotateCcw } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { EvidenceLevel, ObjectiveProgress, SkillProgress } from "@/lib/api/types";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { SKILL_STANDINGS, evidenceDots, evidenceLabel, objectiveStandingLabel, standingHelp } from "@/features/learning/explain";
import { Standing } from "@/features/learning/Standing";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { formatDay } from "@/lib/utils/date";
import { cn } from "@/lib/utils/cn";
import { SectionTitle } from "./SectionTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

/**
 * What the evidence says, skill by skill (directive §13.3). Evidence strength and
 * performance are shown separately: "not started" and "too early to tell" are
 * never presented as weakness, and a claim about performance only appears once
 * there is enough evidence for it (the server's EvidenceStandard).
 */
export function SkillsSection({ childId }: { childId: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.skills(childId), [childId]);
  const m = useMessages(familyMessages).skills;

  return (
    <section aria-labelledby="skills" className="flex flex-col gap-5">
      <SectionTitle id="skills" title={m.title} subtitle={m.subtitle} />

      {isLoading && !data && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {data && data.length === 0 && (
        <EmptyState title={m.none} description={m.noneLead} />
      )}
      {data?.map((curriculum) => (
        <div key={curriculum.enrollmentId} className="flex flex-col gap-4">
          {data.length > 1 && <h3 className="font-display text-lg font-semibold text-tonle-950">{curriculum.curriculum.name}</h3>}
          {curriculum.objectives.map((objective, i) => (
            <ObjectiveTable key={objective.objectiveId} childId={childId} objective={objective} position={i + 1} />
          ))}
        </div>
      ))}

      <details className="group rounded-paper bg-white px-6 py-4 ring-1 ring-line/70 open:pb-6">
        <summary className="cursor-pointer list-none text-sm font-semibold text-tonle-700 marker:hidden">
          <span className="inline-flex items-center gap-2">
            {m.howWeDescribe}
            <span aria-hidden className="text-ink-faint transition-transform duration-200 group-open:rotate-90">
              ›
            </span>
          </span>
        </summary>
        <div className="mt-4 grid gap-6 text-sm text-ink-soft sm:grid-cols-2">
          <div className="flex flex-col gap-3">
            <p>
              {m.everyAnswer} <strong className="text-ink">{m.tooEarly}</strong>
              {m.notWeak}
            </p>
            <ul className="flex flex-col gap-2">
              {(Object.keys(evidenceDots) as EvidenceLevel[]).map((level) => (
                <li key={level} className="flex items-center gap-3">
                  <EvidenceLine level={level} />
                  <span>{evidenceLabel(level)}</span>
                </li>
              ))}
            </ul>
          </div>
          <ul className="flex flex-col gap-2.5">
            {SKILL_STANDINGS.map((standing) => (
              <li key={standing} className="flex flex-col">
                <Standing standing={standing} />
                <span className="pl-5">{standingHelp(standing)}</span>
              </li>
            ))}
          </ul>
        </div>
      </details>
    </section>
  );
}

/**
 * One chapter's skills. A chapter with answers opens its table; one not started yet stays a
 * single line until asked, so a long curriculum reads as its chapters, not as every skill.
 */
function ObjectiveTable({ childId, objective, position }: { childId: string; objective: ObjectiveProgress; position: number }) {
  const started = objective.skillsPractised > 0;
  const m = useMessages(familyMessages).skills;
  return (
    <article className="overflow-hidden rounded-paper bg-white shadow-card">
      <header className="flex flex-wrap items-start justify-between gap-4 px-6 pb-5 pt-6 sm:px-8">
        <div className="max-w-xl">
          <h4 className="font-display text-lg font-semibold text-tonle-950">
            <span className="tabular text-ink-faint">{m.chapter(position)}</span>
            {objective.title}
          </h4>
          {objective.description && <p className="mt-1 text-sm text-ink-soft">{objective.description}</p>}
          <p className="mt-2 tabular text-sm text-ink-faint">
            {m.practised(objective.skillsPractised, objective.skillsMapped, objective.skillsWithSufficientEvidence)}
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
          {objectiveStandingLabel(objective.standing)}
        </span>
      </header>
      {started ? (
        <SkillTable childId={childId} objective={objective} />
      ) : (
        <details className="group border-t border-line">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-6 py-3.5 text-sm font-semibold text-tonle-700 marker:hidden hover:bg-paper sm:px-8">
            <span aria-hidden className="text-ink-faint transition-transform duration-200 group-open:rotate-90">
              ›
            </span>
            {m.showIts(objective.skills.length)}
          </summary>
          <SkillTable childId={childId} objective={objective} />
        </details>
      )}
    </article>
  );
}

function SkillTable({ childId, objective }: { childId: string; objective: ObjectiveProgress }) {
  const m = useMessages(familyMessages).skills;
  return (
    <div role="table" aria-label={m.skillsIn(objective.title)}>
      <div
        role="row"
        className="hidden grid-cols-[minmax(0,1fr)_150px_150px] gap-4 border-y border-line bg-paper px-8 py-2.5 text-[0.8125rem] font-semibold text-ink-faint sm:grid"
      >
        <span role="columnheader">{m.skill}</span>
        <span role="columnheader">{m.evidence}</span>
        <span role="columnheader">{m.shows}</span>
      </div>
      <div role="rowgroup" className="divide-y divide-line border-t border-line sm:border-t-0">
        {objective.skills.map((skill) => (
          <SkillRow key={skill.skillId} childId={childId} skill={skill} />
        ))}
      </div>
    </div>
  );
}

function SkillRow({ childId, skill }: { childId: string; skill: SkillProgress }) {
  const m = useMessages(familyMessages).skills;
  return (
    <div role="row" className="grid gap-3 px-6 py-4 sm:grid-cols-[minmax(0,1fr)_150px_150px] sm:items-center sm:gap-4 sm:px-8">
      <div role="cell">
        {/* X19: each skill opens its evidence and what happens next. */}
        <Link
          to={`/parent/children/${childId}/skills/${skill.skillId}`}
          className="group inline-flex items-center gap-1 font-semibold text-tonle-800 underline-offset-4 hover:underline"
        >
          {skill.name}
          <ChevronRight
            size={15}
            aria-hidden
            className="text-ink-faint transition-transform duration-150 group-hover:translate-x-0.5"
          />
        </Link>
        <p className="tabular text-sm text-ink-soft">
          {skill.scoredAnswers > 0
            ? m.answersCorrect(skill.correctAnswers, skill.scoredAnswers, skill.lastPracticedAt ? formatDay(skill.lastPracticedAt) : null)
            : m.noAnswers}
        </p>
        {skill.inReview && (
          <p className="mt-1 flex items-center gap-1.5 text-sm font-semibold text-light-deep">
            <RotateCcw size={14} aria-hidden /> {m.inReview}
            {skill.reviewDueAt ? m.due(formatDay(skill.reviewDueAt)) : ""}
          </p>
        )}
      </div>
      <div role="cell" className="flex items-center gap-3">
        <EvidenceLine level={skill.evidenceLevel} />
        <span className="text-[0.8125rem] text-ink-soft sm:sr-only">{evidenceLabel(skill.evidenceLevel)}</span>
      </div>
      <div role="cell">
        <Standing standing={skill.standing} />
      </div>
    </div>
  );
}
