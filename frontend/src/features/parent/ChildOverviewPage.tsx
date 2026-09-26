import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Pencil, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import type { Child } from "@/lib/api/types";
import { LearningNowSection } from "./LearningNowSection";
import { SkillsSection } from "./SkillsSection";
import { RecentActivitySection } from "./RecentActivitySection";
import { InsightsSection } from "./InsightsSection";

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

  if (child.isLoading) return <LoadingState label="Loading…" />;
  if (child.error) {
    return (
      <div className="flex flex-col gap-6">
        <BackLink />
        <ErrorState
          error={child.error}
          message={child.error instanceof ApiError && child.error.status === 403 ? "You don't have access to this child." : undefined}
          onRetry={child.refetch}
        />
      </div>
    );
  }
  if (!child.data) return null;

  return (
    <div className="flex flex-col gap-10">
      <BackLink />
      <Header child={child.data} onRenamed={child.refetch} />
      <LearningNowSection childId={childId} childName={child.data.displayName} enrollments={enrollments} />
      <SkillsSection childId={childId} />
      <RecentActivitySection childId={childId} />
      <InsightsSection childId={childId} />
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/parent" className="flex w-fit items-center gap-2 text-sm font-semibold text-muted hover:text-ink">
      <ArrowLeft size={16} aria-hidden /> Your family
    </Link>
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
    <header className="flex flex-col gap-6 rounded-3xl bg-white p-6 shadow-soft sm:flex-row sm:items-center sm:justify-between sm:p-8">
      <div className="flex items-center gap-5">
        <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-kid-mint text-2xl font-extrabold text-kid-mintDeep">
          {child.displayName.charAt(0).toUpperCase()}
        </span>
        {editing ? (
          <form onSubmit={save} className="flex flex-col gap-2">
            <label htmlFor="child-name" className="text-xs font-semibold uppercase tracking-wide text-muted">
              Child's name
            </label>
            <div className="flex flex-wrap gap-2">
              <input
                id="child-name"
                value={name}
                maxLength={80}
                onChange={(e) => setName(e.target.value)}
                className="rounded-2xl border-[1.5px] border-hairline px-4 py-2 font-semibold text-ink outline-none focus:border-brand-blueStrong"
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? "child-name-error" : undefined}
              />
              <Button type="submit" size="md" disabled={saving || !name.trim()}>
                {saving ? "Saving…" : "Save"}
              </Button>
              <Button
                type="button"
                size="md"
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
              <p id="child-name-error" role="alert" className="text-sm font-medium text-brand-redStrong">
                {error}
              </p>
            )}
          </form>
        ) : (
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">{child.displayName}</h1>
              <button
                type="button"
                onClick={() => setEditing(true)}
                aria-label={`Rename ${child.displayName}`}
                className="rounded-full p-2 text-muted hover:bg-surface-sand hover:text-ink"
              >
                <Pencil size={16} aria-hidden />
              </button>
            </div>
            <p className="text-sm text-muted">Age {ageFromDateOfBirth(child.dateOfBirth)}</p>
          </div>
        )}
      </div>
      <Link
        to={`/learn/${child.id}`}
        className="inline-flex items-center justify-center gap-2 rounded-pill bg-brand-blueStrong px-6 py-3.5 text-base font-semibold text-white shadow-soft hover:bg-blue-700"
      >
        <Play size={18} aria-hidden /> Start {child.displayName}'s session
      </Link>
    </header>
  );
}
