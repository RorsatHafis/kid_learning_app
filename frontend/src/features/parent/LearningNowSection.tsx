import { useState } from "react";
import { ChevronDown, Plus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { EnrollmentOverview, JourneyStep } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { parentReason, stepTag } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { ChooseCurriculumPanel } from "./ChooseCurriculumPanel";
import { SectionTitle } from "./SectionTitle";
import { BookStrip } from "./ui";

type AsyncList = {
  data: EnrollmentOverview[] | null;
  error: Error | null;
  isLoading: boolean;
  refetch: () => void;
};

/** What the child is learning now, and why the next step is next (directive §13.2, §13.4). */
export function LearningNowSection({
  childId,
  childName,
  enrollments,
}: {
  childId: string;
  childName: string;
  enrollments: AsyncList;
}) {
  const [adding, setAdding] = useState(false);
  const list = enrollments.data ?? [];

  return (
    <section aria-labelledby="learning-now" className="flex flex-col gap-4">
      <SectionTitle id="learning-now" title="Learning now" subtitle={`The paths ${childName} is on, and what comes next.`}>
        {list.length > 0 && !adding && (
          <Button variant="outline" size="md" onClick={() => setAdding(true)}>
            <Plus size={16} aria-hidden /> Add a subject
          </Button>
        )}
      </SectionTitle>

      {enrollments.isLoading && !enrollments.data && <LoadingState label="Loading learning paths…" />}
      {enrollments.error && <ErrorState error={enrollments.error} onRetry={enrollments.refetch} />}

      {enrollments.data && (list.length === 0 || adding) && (
        <ChooseCurriculumPanel
          childId={childId}
          excludeCurriculumIds={list.map((e) => e.curriculum.id)}
          onEnrolled={() => {
            setAdding(false);
            enrollments.refetch();
          }}
          onCancel={list.length > 0 ? () => setAdding(false) : undefined}
        />
      )}

      {list.map((enrollment) => (
        <EnrollmentCard key={enrollment.enrollmentId} enrollment={enrollment} />
      ))}
    </section>
  );
}

function EnrollmentCard({ enrollment }: { enrollment: EnrollmentOverview }) {
  const [open, setOpen] = useState(false);
  const { curriculum, completedSteps, totalSteps, nextStep } = enrollment;

  return (
    <article className="rounded-paper bg-white shadow-paper ring-1 ring-line/70">
      <div className="flex flex-col gap-6 p-6 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h3 className="font-display text-xl font-semibold text-tonle-950">{curriculum.name}</h3>
            <p className="mt-0.5 text-sm text-ink-faint">{[curriculum.subjectName, curriculum.ageHubName].filter(Boolean).join(" · ")}</p>
          </div>
          <div className="flex flex-col items-start gap-2 sm:items-end">
            <BookStrip done={completedSteps} total={totalSteps} label={`${curriculum.name}: steps completed`} next={nextStep !== null} />
            <p className="tabular text-sm font-semibold text-ink">
              {completedSteps} of {totalSteps} steps completed
            </p>
          </div>
        </div>

        {nextStep ? (
          <div className="grid gap-4 border-t border-line pt-6 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] sm:gap-8">
            <div>
              <p className="text-sm font-semibold text-tonle-600">Up next</p>
              <p className="mt-1 font-display text-lg font-semibold text-tonle-950">{nextStep.title}</p>
            </div>
            <p className="flex gap-3 text-[0.9375rem] leading-relaxed text-ink">
              <Sparkle />
              <span>
                <span className="font-semibold">Why this next? </span>
                {parentReason(nextStep, curriculum.name)}
              </span>
            </p>
          </div>
        ) : (
          <p className="border-t border-line pt-6 text-[0.9375rem] text-ink-soft">
            All steps on this path are done for now. New steps appear here when the platform schedules practice or review.
          </p>
        )}
        <p className="text-[0.8125rem] text-ink-faint">Steps completed show activity, not mastery — Skills & evidence says what the answers show.</p>
      </div>

      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 border-t border-line px-6 py-4 text-sm font-semibold text-tonle-700 transition-colors hover:bg-paper sm:px-8"
      >
        {open ? "Hide the full path" : "Show the full path"}
        <ChevronDown size={16} className={cn("transition-transform duration-200 ease-out", open && "rotate-180")} aria-hidden />
      </button>
      {open && (
        <div className="border-t border-line px-6 pb-6 sm:px-8">
          <FullPath enrollmentId={enrollment.enrollmentId} curriculumName={curriculum.name} />
        </div>
      )}
    </article>
  );
}

/** A small spark of light: marks the platform's own reasoning. */
function Sparkle() {
  return (
    <svg viewBox="0 0 16 16" className="mt-1 h-4 w-4 shrink-0 text-light" aria-hidden>
      <path d="M8 0.8 C8.4 4.6 9.6 5.8 13.4 6.2 C9.6 6.6 8.4 7.8 8 11.6 C7.6 7.8 6.4 6.6 2.6 6.2 C6.4 5.8 7.6 4.6 8 0.8 Z" fill="currentColor" />
    </svg>
  );
}

function FullPath({ enrollmentId, curriculumName }: { enrollmentId: string; curriculumName: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.journey(enrollmentId), [enrollmentId]);
  if (isLoading) return <LoadingState label="Loading the path…" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return null;
  return (
    <ol className="flex flex-col">
      {data.steps.map((step, i) => (
        <PathRow key={step.learningPathItemId} step={step} index={i} isNext={step.learningPathItemId === data.nextStepId} curriculumName={curriculumName} />
      ))}
    </ol>
  );
}

function PathRow({ step, index, isNext, curriculumName }: { step: JourneyStep; index: number; isNext: boolean; curriculumName: string }) {
  const tag = stepTag(step);
  const done = step.status === "COMPLETED";
  return (
    <li className="grid grid-cols-[2rem_minmax(0,1fr)] gap-3 border-b border-line py-4 last:border-b-0">
      <span
        className={cn(
          "mt-0.5 flex h-7 w-7 items-center justify-center rounded-[7px] text-xs font-bold tabular",
          done ? "bg-light text-tonle-950" : isNext ? "bg-white text-tonle-800 ring-2 ring-light" : "bg-tonle-50 text-ink-faint",
        )}
        aria-label={done ? "Completed" : isNext ? "Up next" : "Not started"}
      >
        {index + 1}
      </span>
      <div>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className={cn("font-semibold", done || isNext ? "text-ink" : "text-ink-soft")}>{step.title}</span>
          {tag && <span className="text-[0.8125rem] font-semibold text-dusk-deep">{tag.label}</span>}
          {done && step.score !== null && <span className="tabular text-sm text-ink-faint">Score {Math.round(step.score)}%</span>}
        </div>
        {step.explanation.reason !== "CURRICULUM" && <p className="mt-0.5 text-sm text-ink-soft">{parentReason(step, curriculumName)}</p>}
      </div>
    </li>
  );
}
