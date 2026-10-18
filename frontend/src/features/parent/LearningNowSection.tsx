import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { ChevronDown, Plus } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { enrollmentApi, learningApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import type { EnrollmentOverview, JourneyStep, Chapter } from "@/lib/api/types";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { parentReason, stepTag } from "@/features/learning/explain";
import { cn } from "@/lib/utils/cn";
import { ChooseCurriculumPanel } from "./ChooseCurriculumPanel";
import { SectionTitle } from "./SectionTitle";
import { ChapterTrack } from "./ui";
import { chapterRuns } from "@/features/learning/chapters";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";
import { subjectTheme } from "@/features/learning/subjectTheme";

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
  // ?add=subject (the family home's "Add a subject" link) opens the choice straight away.
  const [params, setParams] = useSearchParams();
  const [adding, setAddingState] = useState(params.get("add") === "subject");
  const setAdding = (open: boolean) => {
    setAddingState(open);
    if (!open && params.has("add")) setParams({}, { replace: true });
  };
  const section = useRef<HTMLElement>(null);
  useEffect(() => {
    if (params.get("add") === "subject") section.current?.scrollIntoView?.({ block: "start" });
    // Only when the page opens with the link.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const list = enrollments.data ?? [];
  const m = useMessages(familyMessages).learningNow;

  return (
    <section ref={section} aria-labelledby="learning-now" className="flex flex-col gap-4">
      <SectionTitle id="learning-now" title={m.title} subtitle={m.subtitle(childName)}>
        {list.length > 0 && !adding && (
          <Button variant="outline" size="md" onClick={() => setAdding(true)}>
            <Plus size={16} aria-hidden /> {m.addSubject}
          </Button>
        )}
      </SectionTitle>

      {enrollments.isLoading && !enrollments.data && <LoadingState label={m.loading} />}
      {enrollments.error && <ErrorState error={enrollments.error} onRetry={enrollments.refetch} />}

      {enrollments.data && (list.length === 0 || adding) && (
        <ChooseCurriculumPanel
          childId={childId}
          childName={childName}
          excludeCurriculumIds={list.map((e) => e.curriculum.id)}
          onEnrolled={() => {
            setAdding(false);
            enrollments.refetch();
          }}
          onCancel={list.length > 0 ? () => setAdding(false) : undefined}
        />
      )}

      {list.map((enrollment) => (
        <EnrollmentCard
          key={enrollment.enrollmentId}
          enrollment={enrollment}
          childName={childName}
          onStopped={enrollments.refetch}
        />
      ))}
    </section>
  );
}

function EnrollmentCard({
  enrollment,
  childName,
  onStopped,
}: {
  enrollment: EnrollmentOverview;
  childName: string;
  onStopped: () => void;
}) {
  const [open, setOpen] = useState(false);
  const { curriculum, completedSteps, totalSteps, nextStep } = enrollment;
  const m = useMessages(familyMessages).learningNow;
  const ch = useMessages(familyMessages).chapters;

  return (
    <article className="overflow-hidden rounded-paper bg-white shadow-card">
      <span aria-hidden className={cn("block h-1.5", subjectTheme(curriculum.subjectCode, curriculum.subjectName).solid)} />
      <div className="flex flex-col gap-6 p-6 sm:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-4">
            <SubjectEmblem code={curriculum.subjectCode} name={curriculum.subjectName} size={56} />
            <div>
              <h3 className="font-display text-xl font-bold text-tonle-950">{curriculum.name}</h3>
              <p className="mt-0.5 text-sm text-ink-faint">{[curriculum.subjectName, curriculum.ageHubName].filter(Boolean).join(" · ")}</p>
            </div>
          </div>
          <p className="tabular text-sm font-semibold text-ink">
            {m.completed(completedSteps, totalSteps)}
          </p>
        </div>

        <ChapterTrack chapters={enrollment.chapters} label={ch.label(curriculum.name)} />

        {nextStep ? (
          <div className="grid gap-4 border-t border-line pt-6 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] sm:gap-8">
            <div>
              <p className="text-sm font-semibold text-tonle-600">{m.upNext}</p>
              <p className="mt-1 font-display text-lg font-semibold text-tonle-950">{nextStep.title}</p>
            </div>
            <p className="flex gap-3 text-[0.9375rem] leading-relaxed text-ink">
              <Sparkle />
              <span>
                <span className="font-semibold">{m.whyNext} </span>
                {parentReason(nextStep, curriculum.name, enrollment.currentChapter)}
              </span>
            </p>
          </div>
        ) : (
          <p className="border-t border-line pt-6 text-[0.9375rem] text-ink-soft">
            {m.allDone}
          </p>
        )}
        <p className="text-[0.8125rem] text-ink-faint">{m.notMastery}</p>
        <StopPath enrollment={enrollment} childName={childName} onStopped={onStopped} />
      </div>

      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 border-t border-line px-6 py-4 text-sm font-semibold text-tonle-700 transition-colors hover:bg-paper sm:px-8"
      >
        {open ? m.hidePath : m.showPath}
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

/**
 * Stopping a path (Y19): the family's way to change subject, its consequence said before it happens.
 * The server refuses a path the child's class assigns, and says so.
 */
function StopPath({
  enrollment,
  childName,
  onStopped,
}: {
  enrollment: EnrollmentOverview;
  childName: string;
  onStopped: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const name = enrollment.curriculum.name;
  const m = useMessages(familyMessages).learningNow;

  async function stop() {
    setBusy(true);
    setError(null);
    try {
      await enrollmentApi.stop(enrollment.enrollmentId);
      onStopped();
    } catch (err) {
      setError(problemMessage(err, m.stopFailed));
      setBusy(false);
    }
  }

  if (!confirming) {
    return (
      <Button variant="ghost" size="sm" className="-ml-2.5 w-fit" onClick={() => setConfirming(true)}>
        {m.stop}
        <span className="sr-only">: {name}</span>
      </Button>
    );
  }
  return (
    <div role="group" aria-label={m.stopLabel(name)} className="flex flex-col gap-3 rounded-[12px] bg-paper p-4 ring-1 ring-line">
      <p className="text-sm text-ink">{m.stopExplain(childName, name)}</p>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" disabled={busy} onClick={() => void stop()}>
          {busy ? m.stopping : m.stopConfirm}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          disabled={busy}
          onClick={() => {
            setConfirming(false);
            setError(null);
          }}
        >
          {m.keep}
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </div>
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
  const m = useMessages(familyMessages).learningNow;
  const ch = useMessages(familyMessages).chapters;
  if (isLoading) return <LoadingState label={m.loadingPath} />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return null;
  return (
    <div className="flex flex-col gap-2 pt-2">
      {chapterRuns(data).map((run, r) => (
        <section key={`${run.chapter?.id ?? "extra"}-${r}`} aria-label={run.chapter ? ch.titleOf(run.chapter.position, run.chapter.title) : ch.extraPractice}>
          {run.chapter && (
            <h4 className="flex flex-wrap items-baseline justify-between gap-x-3 pt-4 text-sm font-semibold text-tonle-950">
              <span>{ch.run(run.chapter.position, run.chapter.title)}</span>
              <span className="tabular text-[0.8125rem] font-medium text-ink-faint">
                {ch.runDone(run.chapter.completedSteps, run.chapter.totalSteps)}
              </span>
            </h4>
          )}
          <ol className="flex flex-col">
            {run.steps.map(({ step, index }) => (
              <PathRow
                key={step.learningPathItemId}
                step={step}
                index={index}
                isNext={step.learningPathItemId === data.nextStepId}
                curriculumName={curriculumName}
                chapter={run.chapter}
              />
            ))}
          </ol>
        </section>
      ))}
    </div>
  );
}

function PathRow({
  step,
  index,
  isNext,
  curriculumName,
  chapter,
}: {
  step: JourneyStep;
  index: number;
  isNext: boolean;
  curriculumName: string;
  chapter: Chapter | null;
}) {
  const tag = stepTag(step);
  const done = step.status === "COMPLETED";
  const m = useMessages(familyMessages).learningNow;
  return (
    <li className="grid grid-cols-[2rem_minmax(0,1fr)] gap-3 border-b border-line py-4 last:border-b-0">
      <span
        className={cn(
          "mt-0.5 flex h-7 w-7 items-center justify-center rounded-[7px] text-xs font-bold tabular",
          done ? "bg-light text-tonle-950" : isNext ? "bg-white text-tonle-800 ring-2 ring-light" : "bg-tonle-50 text-ink-faint",
        )}
        aria-label={done ? m.completedStep : isNext ? m.upNext : m.notStarted}
      >
        {index + 1}
      </span>
      <div>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className={cn("font-semibold", done || isNext ? "text-ink" : "text-ink-soft")}>{step.title}</span>
          {tag && <span className="text-[0.8125rem] font-semibold text-dusk-deep">{tag.label}</span>}
          {done && step.score !== null && <span className="tabular text-sm text-ink-faint">{m.score(Math.round(step.score))}</span>}
        </div>
        {step.explanation.reason !== "CURRICULUM" && <p className="mt-0.5 text-sm text-ink-soft">{parentReason(step, curriculumName, chapter)}</p>}
      </div>
    </li>
  );
}
