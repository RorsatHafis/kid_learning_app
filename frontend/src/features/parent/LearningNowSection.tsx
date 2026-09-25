import { useState } from "react";
import { CheckCircle2, ChevronDown, Circle, PlayCircle, Plus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { learningApi } from "@/lib/api/endpoints";
import type { EnrollmentOverview, JourneyStep } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";
import { parentReason, stepTag } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { ChooseCurriculumPanel } from "./ChooseCurriculumPanel";
import { SectionTitle } from "./SectionTitle";

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

      {enrollments.isLoading && <LoadingState label="Loading learning paths…" />}
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
  const ratio = totalSteps > 0 ? completedSteps / totalSteps : 0;

  return (
    <article className="flex flex-col gap-5 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-bold text-ink">{curriculum.name}</h3>
          <div className="mt-1 flex flex-wrap gap-2">
            {curriculum.subjectName && (
              <Pill tone="blue" className="px-2.5 py-1 text-xs">
                {curriculum.subjectName}
              </Pill>
            )}
            {curriculum.ageHubName && (
              <Pill tone="neutral" className="px-2.5 py-1 text-xs">
                {curriculum.ageHubName}
              </Pill>
            )}
          </div>
        </div>
        <p className="text-sm font-semibold text-muted">
          {completedSteps} of {totalSteps} steps completed
        </p>
      </div>

      <div
        className="h-2.5 overflow-hidden rounded-pill bg-hairline"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={totalSteps}
        aria-valuenow={completedSteps}
        aria-label={`${curriculum.name}: steps completed`}
      >
        <div className="h-full rounded-pill bg-kid-mintDeep" style={{ width: `${ratio * 100}%` }} />
      </div>
      <p className="-mt-2 text-xs text-muted">Steps completed show activity, not mastery — see Skills below for what the evidence says.</p>

      {nextStep ? (
        <div className="rounded-2xl bg-kid-lavender/60 p-4">
          <p className="text-xs font-bold uppercase tracking-wide text-kid-lavenderDeep">Up next</p>
          <p className="font-bold text-ink">{nextStep.title}</p>
          <p className="mt-1 text-sm text-ink">
            <span className="font-semibold">Why this next? </span>
            {parentReason(nextStep, curriculum.name)}
          </p>
        </div>
      ) : (
        <p className="rounded-2xl bg-kid-mint/60 p-4 text-sm font-semibold text-kid-mintDeep">
          All steps on this path are done for now. New steps appear here when the platform schedules practice or review.
        </p>
      )}

      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-fit items-center gap-1 text-sm font-semibold text-brand-blueStrong hover:underline"
      >
        {open ? "Hide the full path" : "Show the full path"}
        <ChevronDown size={16} className={cn("transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      {open && <FullPath enrollmentId={enrollment.enrollmentId} curriculumName={curriculum.name} />}
    </article>
  );
}

function FullPath({ enrollmentId, curriculumName }: { enrollmentId: string; curriculumName: string }) {
  const { data, error, isLoading, refetch } = useAsync(() => learningApi.journey(enrollmentId), [enrollmentId]);
  if (isLoading) return <LoadingState label="Loading the path…" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return null;
  return (
    <ol className="flex flex-col divide-y divide-hairline rounded-2xl border border-hairline">
      {data.steps.map((step) => (
        <PathRow key={step.learningPathItemId} step={step} isNext={step.learningPathItemId === data.nextStepId} curriculumName={curriculumName} />
      ))}
    </ol>
  );
}

function PathRow({ step, isNext, curriculumName }: { step: JourneyStep; isNext: boolean; curriculumName: string }) {
  const tag = stepTag(step);
  const done = step.status === "COMPLETED";
  const Icon = done ? CheckCircle2 : isNext ? PlayCircle : Circle;
  return (
    <li className="flex gap-3 p-4">
      <Icon
        size={20}
        className={cn("mt-0.5 shrink-0", done ? "text-kid-mintDeep" : isNext ? "text-brand-blueStrong" : "text-hairline")}
        aria-label={done ? "Completed" : isNext ? "Up next" : "Not started"}
      />
      <div className="flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold text-ink">{step.title}</span>
          {tag && (
            <Pill tone={tag.tone === "sun" ? "amber" : tag.tone === "lavender" ? "purple" : "red"} className="px-2 py-0.5 text-[11px]">
              {tag.label}
            </Pill>
          )}
          {done && step.score !== null && <span className="text-sm text-muted">Score {Math.round(step.score)}%</span>}
        </div>
        {step.explanation.reason !== "CURRICULUM" && (
          <p className="mt-0.5 text-sm text-muted">{parentReason(step, curriculumName)}</p>
        )}
      </div>
    </li>
  );
}
