import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronRight, Pencil, Play } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { childrenApi, learningApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button, buttonClasses } from "@/components/ui/Button";
import { fieldClasses } from "@/features/auth/FormField";
import { ChildPortrait, SceneBand } from "./ui";
import { ageFromDateOfBirth } from "@/lib/utils/date";
import type { Child } from "@/lib/api/types";
import { LearningNowSection } from "./LearningNowSection";
import { ChangesSection } from "./ChangesSection";
import { SkillsSection } from "./SkillsSection";
import { RecentActivitySection } from "./RecentActivitySection";
import { InsightsSection } from "./InsightsSection";
import { SchoolAccessSection } from "./SchoolAccessSection";
import { DeleteChildSection } from "./Deletion";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

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
  const m = useMessages(familyMessages).overview;
  useDocumentTitle(child.data?.displayName);

  if (child.isLoading) return <LoadingState />;
  if (child.error) {
    return (
      <div className="flex flex-col gap-6">
        <BackLink />
        <ErrorState
          error={child.error}
          message={
            child.error instanceof ApiError && child.error.status === 404
              ? m.notFound
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
        {/* "What is improving?": change over time, right after what is happening now. */}
        <div className="min-w-0 lg:col-start-1">
          <ChangesSection childId={childId} childName={child.data.displayName} />
        </div>
        <aside className="lg:sticky lg:top-24 lg:col-start-2 lg:row-span-5 lg:row-start-1 lg:self-start">
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
      <DeleteChildSection child={child.data} />
    </div>
  );
}

function BackLink({ name }: { name?: string }) {
  const m = useMessages(familyMessages).overview;
  return (
    <nav aria-label={m.breadcrumb}>
      <ol className="flex items-center gap-1.5 text-sm">
        <li>
          <Link to="/parent" className="font-semibold text-tonle-700 underline-offset-4 hover:underline">
            {m.yourFamily}
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
  const [birth, setBirth] = useState(child.dateOfBirth);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const m = useMessages(familyMessages).overview;

  async function save(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await childrenApi.update(child.id, {
        displayName: name,
        ...(birth !== child.dateOfBirth ? { dateOfBirth: birth } : {}),
      });
      setEditing(false);
      onRenamed();
    } catch (err) {
      setError(
        err instanceof ApiError && err.validationErrors?.displayName
          ? m.saveNameFailed
          : err instanceof ApiError && (err.validationErrors?.dateOfBirth || err.status === 400)
            ? m.saveBirthFailed
            : m.saveFailed,
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <SceneBand>
      <header className="flex flex-col items-start gap-6 px-6 py-8 sm:px-10 sm:py-10">
        <div className="flex items-end gap-5">
          <ChildPortrait buddy={child.learningBuddy} size="lg" />
          {editing ? (
            <form onSubmit={save} className="flex flex-col gap-2">
              <label htmlFor="child-name" className="text-sm font-semibold text-ink">
                {m.childName}
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
              </div>
              <label htmlFor="child-birth" className="text-sm font-semibold text-ink">
                {m.dateOfBirth}
              </label>
              <div className="flex flex-wrap gap-2">
                <input
                  id="child-birth"
                  type="date"
                  value={birth}
                  max={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => setBirth(e.target.value)}
                  className={`${fieldClasses} h-11 w-56`}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? "child-name-error" : undefined}
                />
                <Button type="submit" disabled={saving || !name.trim() || !birth}>
                  {saving ? m.saving : m.save}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setEditing(false);
                    setName(child.displayName);
                    setBirth(child.dateOfBirth);
                    setError(null);
                  }}
                >
                  {m.cancel}
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
                  aria-label={m.edit(child.displayName)}
                  className="rounded-[10px] p-2 text-ink-faint transition-colors hover:bg-white hover:text-tonle-800"
                >
                  <Pencil size={16} aria-hidden />
                </button>
              </div>
              <p className="text-[0.9375rem] text-ink-soft">{m.age(ageFromDateOfBirth(child.dateOfBirth))}</p>
            </div>
          )}
        </div>
        <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "lg", "w-full sm:w-auto")}>
          <Play size={18} aria-hidden /> {m.startSession(child.displayName)}
        </Link>
      </header>
    </SceneBand>
  );
}
