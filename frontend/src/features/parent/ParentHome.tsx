import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Plus, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button, buttonClasses } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { chapterOf } from "@/features/learning/chapters";
import { AddChildForm } from "./AddChildForm";
import { lastPlayedText } from "./familyText";
import { ChildSeal, SceneBand } from "./ui";
import { PendingChildChapter } from "./Deletion";
import type { Child, EnrollmentOverview } from "@/lib/api/types";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

/** The family: each child as a chapter — who they are, where they are on their path, what's next. */
export function ParentHome() {
  const { data: children, error, isLoading, refetch } = useAsync(() => childrenApi.list(), []);
  const [isAdding, setIsAdding] = useState(false);
  const m = useMessages(familyMessages).home;
  useDocumentTitle(m.title);
  const count = children?.length ?? 0;

  return (
    <div className="flex flex-col gap-10">
      <SceneBand>
        <div className="flex min-h-[220px] flex-col justify-end gap-3 px-6 pb-8 pt-12 sm:min-h-[260px] sm:px-10">
          <h1 className="font-display text-headline font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
            {m.title}
          </h1>
          <p className="max-w-md text-[1.0625rem] leading-relaxed text-ink-soft">{count === 0 ? m.leadEmpty : m.lead}</p>
        </div>
      </SceneBand>

      {isLoading && !children && <LoadingState label={m.loading} />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && (
        <section aria-label={m.children} className="overflow-hidden rounded-paper bg-white shadow-paper ring-1 ring-line/70">
          {children.length > 0 && (
            <ul className="divide-y divide-line">
              {children.map((child) => (
                <li key={child.id}>
                  {child.status === "DELETION_PENDING" ? (
                    <PendingChildChapter child={child} onKept={refetch} />
                  ) : (
                    <ChildChapter child={child} />
                  )}
                </li>
              ))}
            </ul>
          )}
          <div className={children.length > 0 ? "border-t border-line" : undefined}>
            {isAdding ? (
              <div className="p-6 sm:p-8">
                <AddChildForm
                  onCreated={() => {
                    setIsAdding(false);
                    refetch();
                  }}
                  onCancel={() => setIsAdding(false)}
                />
              </div>
            ) : children.length === 0 ? (
              <div className="flex flex-col items-center gap-4 px-6 py-14 text-center">
                <p className="font-display text-xl font-semibold text-tonle-950">{m.noChildren}</p>
                <p className="max-w-sm text-sm text-ink-soft">{m.noChildrenLead}</p>
                <Button onClick={() => setIsAdding(true)}>
                  <Plus size={17} aria-hidden /> {m.addFirst}
                </Button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setIsAdding(true)}
                className="flex w-full items-center gap-3 px-6 py-5 text-left text-[0.9375rem] font-semibold text-tonle-700 transition-colors duration-150 hover:bg-paper sm:px-8"
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-[10px] border border-dashed border-tonle-300">
                  <Plus size={17} aria-hidden />
                </span>
                {m.addChild}
              </button>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

/** Where a path stands, in words that don't overclaim: finishing every step is not mastery. */
function pathState(enrollment: EnrollmentOverview, m: typeof familyMessages.en.home): ReactNode {
  if (enrollment.nextStep === null) {
    return enrollment.totalSteps > 0 && enrollment.completedSteps >= enrollment.totalSteps ? (
      <span className="text-ink-soft">{m.everyStepDone}</span>
    ) : (
      <span className="text-ink-soft">{m.nothingWaiting}</span>
    );
  }
  const chapter = enrollment.currentChapter ? chapterOf(enrollment.currentChapter, enrollment.chapterCount) : null;
  return (
    <>
      {chapter && <span className="tabular text-ink-soft">{chapter} · </span>}
      <span>{enrollment.currentChapter?.title ?? m.next(enrollment.nextStep.title)}</span>
      <span className="tabular text-ink-faint"> · {m.steps(enrollment.completedSteps, enrollment.totalSteps)}</span>
    </>
  );
}

function ChildChapter({ child }: { child: Child }) {
  const { data: enrollments, error, isLoading } = useAsync(() => learningApi.enrollments(child.id), [child.id]);
  // The most recent finished activity only - enough to say when the child last played.
  const recent = useAsync(() => learningApi.history(child.id, 1), [child.id]);
  const lastPlayed = recent.data?.[0]?.completedAt ?? null;
  const m = useMessages(familyMessages).home;
  const allDone =
    enrollments !== null &&
    enrollments.length > 0 &&
    enrollments.every((e) => e.nextStep === null && e.totalSteps > 0 && e.completedSteps >= e.totalSteps);

  return (
    <article className="grid gap-6 px-6 py-7 sm:px-8 lg:grid-cols-[200px_minmax(0,1fr)_auto] lg:items-center lg:gap-10">
      <Link to={`/parent/children/${child.id}`} className="group flex items-center gap-4">
        <ChildSeal id={child.id} name={child.displayName} />
        <div>
          <h2 className="font-display text-2xl font-semibold text-tonle-950 group-hover:underline group-hover:decoration-tonle-300 group-hover:underline-offset-4">
            {child.displayName}
          </h2>
          <p className="text-sm text-ink-faint">{m.age(ageFromDateOfBirth(child.dateOfBirth))}</p>
          {recent.data && <p className="text-sm text-ink-faint">{lastPlayed ? m.played(lastPlayedText(lastPlayed)) : m.notPlayed}</p>}
        </div>
      </Link>

      <div className="min-w-0">
        {isLoading && !enrollments && <p className="text-sm text-ink-faint">{m.loadingLearning}</p>}
        {error && <p className="text-sm text-ink-soft">{m.learningFailed}</p>}
        {enrollments && enrollments.length === 0 && <p className="text-sm text-ink-soft">{m.noPath}</p>}
        {enrollments && enrollments.length > 0 && (
          <ul className="flex flex-col gap-1.5" aria-label={m.subjectsOf(child.displayName)}>
            {enrollments.map((enrollment) => (
              <li key={enrollment.enrollmentId} className="text-[0.9375rem] leading-snug text-ink">
                <span className="font-semibold text-tonle-950">{enrollment.curriculum.subjectName ?? enrollment.curriculum.name}</span>
                <span className="text-ink-faint"> — </span>
                {pathState(enrollment, m)}
              </li>
            ))}
          </ul>
        )}
        {allDone && (
          <Link
            to={`/parent/children/${child.id}?add=subject`}
            className="mt-2 inline-flex items-center gap-1.5 text-sm font-semibold text-tonle-700 hover:text-tonle-900"
          >
            <Plus size={15} aria-hidden /> {m.addSubjectFor(child.displayName)}
          </Link>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "md")}>
          <Play size={16} aria-hidden /> {m.startSession(child.displayName)}
        </Link>
        <Link to={`/parent/children/${child.id}`} className={buttonClasses("ghost", "md")}>
          {m.seeProgress} <ArrowRight size={16} aria-hidden />
        </Link>
      </div>
    </article>
  );
}
