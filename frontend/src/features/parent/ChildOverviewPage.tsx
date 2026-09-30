import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronRight, Pencil, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button, buttonClasses } from "@/components/ui/Button";
import { fieldClasses } from "@/features/auth/FormField";
import { ChildSeal, SceneBand } from "./ui";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import type { Child } from "@/lib/api/types";
import { LearningNowSection } from "./LearningNowSection";
import { SkillsSection } from "./SkillsSection";
import { RecentActivitySection } from "./RecentActivitySection";
import { InsightsSection } from "./InsightsSection";
import { SchoolAccessSection } from "./SchoolAccessSection";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";

export function ChildOverviewPage() {
  // key={childId} forces a full remount when switching children, so no state from
  // the previous child survives a client-side navigation.
  const { childId = "" } = useParams<{ childId: string }>();
  return <ChildOverview key={childId} childId={childId} />;
}

/**
 * The parent's view of one child (directive §13.2–13.4): what they are learning
 * now and why the next step is next, what the evidence says per skill, what they
 * have done recently and what the platform decided after it, and the current
 * insights. Every claim is rendered from a server read model; nothing is
 * computed or invented here.
 */
function ChildOverview({ childId }: { childId: string }) {
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const enrollments = useAsync(() => learningApi.enrollments(childId), [childId]);
  useDocumentTitle(child.data?.displayName);

  if (child.isLoading) return <LoadingState label="Loading…" />;
  if (child.error) {
    return (
      <div className="flex flex-col gap-6">
        <BackLink />
        <ErrorState
          error={child.error}
          message={
            child.error instanceof ApiError && child.error.status === 404
              ? "We couldn't find this child in your family. They may have been removed, or the link is from another account."
              : undefined
          }
          onRetry={child.refetch}
        />
      </div>
    );
  }
  if (!child.data) return null;

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-col gap-4">
        <BackLink name={child.data.displayName} />
        <Header child={child.data} onRenamed={child.refetch} />
      </div>
      {/* Phone: insights right after the current path — the parent's first question.
          Desktop: insights ride along in a sticky column beside the detail. */}
      <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_340px] lg:gap-x-14 lg:gap-y-14">
        <div className="min-w-0 lg:col-start-1">
          <LearningNowSection childId={childId} childName={child.data.displayName} enrollments={enrollments} />
        </div>
        <aside className="lg:sticky lg:top-24 lg:col-start-2 lg:row-span-4 lg:row-start-1 lg:self-start">
          <InsightsSection childId={childId} />
        </aside>
        {/* Next to the learning a class adds: where a parent with a class code looks. */}
        <div className="min-w-0 lg:col-start-1">
          <SchoolAccessSection childId={childId} childName={child.data.displayName} onJoined={enrollments.refetch} />
        </div>
        <div className="min-w-0 lg:col-start-1">
          <SkillsSection childId={childId} />
        </div>
        <div className="min-w-0 lg:col-start-1">
          <RecentActivitySection childId={childId} />
        </div>
      </div>
    </div>
  );
}

function BackLink({ name }: { name?: string }) {
  return (
    <nav aria-label="Breadcrumb">
      <ol className="flex items-center gap-1.5 text-sm">
        <li>
          <Link to="/parent" className="font-semibold text-tonle-700 underline-offset-4 hover:underline">
            Your family
          </Link>
        </li>
        {name && (
          <>
            <ChevronRight size={14} className="text-ink-faint" aria-hidden />
            <li aria-current="page" className="text-ink-soft">
              {name}
            </li>
          </>
        )}
      </ol>
    </nav>
  );
}

function Header({ child, onRenamed }: { child: Child; onRenamed: () => void }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(child.displayName);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await childrenApi.rename(child.id, name);
      setEditing(false);
      onRenamed();
    } catch (err) {
      setError(
        err instanceof ApiError && err.validationErrors?.displayName
          ? "Please enter a name (up to 80 characters)."
          : "Couldn't save the name. Please try again.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <SceneBand>
      <header className="flex flex-col gap-6 px-6 py-8 sm:flex-row sm:items-end sm:justify-between sm:px-10 sm:py-10">
        <div className="flex items-end gap-5">
          <ChildSeal id={child.id} name={child.displayName} size="lg" />
          {editing ? (
            <form onSubmit={save} className="flex flex-col gap-2">
              <label htmlFor="child-name" className="text-sm font-semibold text-ink">
                Child&apos;s name
              </label>
              <div className="flex flex-wrap gap-2">
                <input
                  id="child-name"
                  value={name}
                  maxLength={80}
                  onChange={(e) => setName(e.target.value)}
                  className={`${fieldClasses} h-11 w-56 font-semibold`}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? "child-name-error" : undefined}
                />
                <Button type="submit" disabled={saving || !name.trim()}>
                  {saving ? "Saving…" : "Save"}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setEditing(false);
                    setName(child.displayName);
                    setError(null);
                  }}
                >
                  Cancel
                </Button>
              </div>
              {error && (
                <p id="child-name-error" role="alert" className="text-sm font-medium text-brick-deep">
                  {error}
                </p>
              )}
            </form>
          ) : (
            <div>
              <div className="flex items-center gap-1">
                <h1 className="font-display text-headline font-semibold text-tonle-950" style={{ fontVariationSettings: '"opsz" 60' }}>
                  {child.displayName}
                </h1>
                <button
                  type="button"
                  onClick={() => setEditing(true)}
                  aria-label={`Rename ${child.displayName}`}
                  className="rounded-[10px] p-2 text-ink-faint transition-colors hover:bg-white hover:text-tonle-800"
                >
                  <Pencil size={16} aria-hidden />
                </button>
              </div>
              <p className="text-[0.9375rem] text-ink-soft">Age {ageFromDateOfBirth(child.dateOfBirth)}</p>
            </div>
          )}
        </div>
        <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "lg", "w-full sm:w-auto")}>
          <Play size={18} aria-hidden /> Start {child.displayName}&apos;s session
        </Link>
      </header>
    </SceneBand>
  );
}
