import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Plus, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button, buttonClasses } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { parentReason } from "@/features/learning/explain";
import { AddChildForm } from "./AddChildForm";
import { BookStrip, ChildSeal, SceneBand } from "./ui";
import type { Child } from "@/lib/api/types";

/** The family: each child as a chapter — who they are, where they are on their path, what's next. */
export function ParentHome() {
  const { data: children, error, isLoading, refetch } = useAsync(() => childrenApi.list(), []);
  const [isAdding, setIsAdding] = useState(false);
  const count = children?.length ?? 0;

  return (
    <div className="flex flex-col gap-10">
      <SceneBand>
        <div className="flex min-h-[220px] flex-col justify-end gap-3 px-6 pb-8 pt-12 sm:min-h-[260px] sm:px-10">
          <h1 className="font-display text-headline font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
            Your family
          </h1>
          <p className="max-w-md text-[1.0625rem] leading-relaxed text-ink-soft">
            {count === 0
              ? "Add your first child to start their Bookeyond Kid learning journey."
              : "What each child is learning, and what comes next."}
          </p>
        </div>
      </SceneBand>

      {isLoading && !children && <LoadingState label="Loading your family…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && (
        <section aria-label="Children" className="overflow-hidden rounded-paper bg-white shadow-paper ring-1 ring-line/70">
          {children.length > 0 && (
            <ul className="divide-y divide-line">
              {children.map((child) => (
                <li key={child.id}>
                  <ChildChapter child={child} />
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
                <p className="font-display text-xl font-semibold text-tonle-950">No children yet</p>
                <p className="max-w-sm text-sm text-ink-soft">Each child gets their own path, their own firefly, and a page here for you.</p>
                <Button onClick={() => setIsAdding(true)}>
                  <Plus size={17} aria-hidden /> Add your first child
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
                Add child
              </button>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

function ChildChapter({ child }: { child: Child }) {
  const { data: enrollments, error, isLoading } = useAsync(() => learningApi.enrollments(child.id), [child.id]);
  const current = enrollments?.find((e) => e.nextStep !== null) ?? enrollments?.[0];

  return (
    <article className="grid gap-6 px-6 py-7 sm:px-8 lg:grid-cols-[240px_minmax(0,1fr)_auto] lg:items-center lg:gap-10">
      <Link to={`/parent/children/${child.id}`} className="group flex items-center gap-4">
        <ChildSeal id={child.id} name={child.displayName} />
        <div>
          <h2 className="font-display text-2xl font-semibold text-tonle-950 group-hover:underline group-hover:decoration-tonle-300 group-hover:underline-offset-4">
            {child.displayName}
          </h2>
          <p className="text-sm text-ink-faint">Age {ageFromDateOfBirth(child.dateOfBirth)}</p>
        </div>
      </Link>

      <div className="min-w-0">
        {isLoading && <p className="text-sm text-ink-faint">Loading learning…</p>}
        {error && <p className="text-sm text-ink-soft">Couldn&apos;t load learning right now.</p>}
        {enrollments && !current && <p className="text-sm text-ink-soft">No learning path chosen yet.</p>}
        {current && (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-6">
            <BookStrip done={current.completedSteps} total={current.totalSteps} label={`${current.curriculum.name}: steps finished`} next={current.nextStep !== null} />
            <div className="min-w-0">
              <p className="text-sm text-ink-faint">
                {current.curriculum.name} · {current.completedSteps} of {current.totalSteps} steps
              </p>
              <p className="truncate text-[0.9375rem] text-ink">
                {current.nextStep ? (
                  <>
                    Next: <span className="font-semibold">{current.nextStep.title}</span>
                  </>
                ) : (
                  "All caught up on this path."
                )}
              </p>
              {current.nextStep && (
                <p className="hidden truncate text-sm text-ink-soft xl:block">{parentReason(current.nextStep, current.curriculum.name)}</p>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "md")}>
          <Play size={16} aria-hidden /> Start {child.displayName}&apos;s session
        </Link>
        <Link to={`/parent/children/${child.id}`} className={buttonClasses("ghost", "md")}>
          See progress <ArrowRight size={16} aria-hidden />
        </Link>
      </div>
    </article>
  );
}
