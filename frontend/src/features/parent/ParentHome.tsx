import { useState } from "react";
import { Link } from "react-router-dom";
import { Plus, ChevronRight, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import { AddChildForm } from "./AddChildForm";
import type { Child } from "@/lib/api/types";

export function ParentHome() {
  const { data: children, error, isLoading, refetch } = useAsync(() => childrenApi.list(), []);
  const [isAdding, setIsAdding] = useState(false);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">Your family</h1>
          <p className="text-sm text-muted">What each child is learning, and what comes next.</p>
        </div>
        {children && children.length > 0 && !isAdding && (
          <Button size="md" onClick={() => setIsAdding(true)}>
            <Plus size={18} aria-hidden /> Add child
          </Button>
        )}
      </div>

      {isAdding && (
        <AddChildForm
          onCreated={() => {
            setIsAdding(false);
            refetch();
          }}
          onCancel={() => setIsAdding(false)}
        />
      )}

      {isLoading && <LoadingState label="Loading your family…" />}
      {error && <ErrorState error={error} onRetry={refetch} />}

      {children && children.length === 0 && !isAdding && (
        <EmptyState
          title="No children yet"
          description="Add your first child to start their Bookeyond Kid learning journey."
          action={
            <Button size="md" onClick={() => setIsAdding(true)} className="mt-2">
              <Plus size={18} aria-hidden /> Add your first child
            </Button>
          }
        />
      )}

      {children && children.length > 0 && (
        <ul className="grid gap-4 md:grid-cols-2">
          {children.map((child) => (
            <li key={child.id}>
              <ChildCard child={child} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ChildCard({ child }: { child: Child }) {
  const { data: enrollments, error, isLoading } = useAsync(() => learningApi.enrollments(child.id), [child.id]);
  const current = enrollments?.find((e) => e.nextStep !== null) ?? enrollments?.[0];

  return (
    <article className="flex h-full flex-col gap-5 rounded-3xl border-[1.5px] border-hairline bg-white p-6 shadow-soft">
      <Link to={`/parent/children/${child.id}`} className="group flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-kid-mint text-xl font-extrabold text-kid-mintDeep">
            {child.displayName.charAt(0).toUpperCase()}
          </span>
          <div>
            <h2 className="text-lg font-bold text-ink group-hover:underline">{child.displayName}</h2>
            <p className="text-sm text-muted">Age {ageFromDateOfBirth(child.dateOfBirth)}</p>
          </div>
        </div>
        <ChevronRight className="text-muted" aria-hidden />
      </Link>

      <div className="flex-1 rounded-2xl bg-surface-cream p-4">
        {isLoading && <p className="text-sm text-muted">Loading learning…</p>}
        {error && <p className="text-sm text-muted">Couldn't load learning right now.</p>}
        {enrollments && !current && <p className="text-sm text-muted">No learning path chosen yet.</p>}
        {current && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="font-semibold text-ink">{current.curriculum.name}</span>
              <span className="text-muted">
                {current.completedSteps} of {current.totalSteps} steps
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-pill bg-hairline" aria-hidden>
              <div
                className="h-full rounded-pill bg-kid-mintDeep"
                style={{ width: `${current.totalSteps ? (current.completedSteps / current.totalSteps) * 100 : 0}%` }}
              />
            </div>
            <p className="text-sm text-muted">
              {current.nextStep ? (
                <>
                  Next: <span className="font-semibold text-ink">{current.nextStep.title}</span>
                </>
              ) : (
                "All caught up on this path."
              )}
            </p>
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <Link
          to={`/learn/${child.id}`}
          className="inline-flex items-center gap-2 rounded-pill bg-brand-blueStrong px-5 py-3 text-sm font-semibold text-white shadow-soft hover:bg-blue-700"
        >
          <Play size={16} aria-hidden /> Start {child.displayName}'s session
        </Link>
        <Link
          to={`/parent/children/${child.id}`}
          className="inline-flex items-center rounded-pill border-[1.5px] border-ink px-5 py-3 text-sm font-semibold text-ink hover:bg-ink hover:text-white"
        >
          See progress
        </Link>
      </div>
    </article>
  );
}
