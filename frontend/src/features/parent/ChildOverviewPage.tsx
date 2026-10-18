import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link, NavLink, Route, Routes, useLocation, useParams } from "react-router-dom";
import { ChevronRight, ExternalLink, Pencil, Play } from "lucide-react";
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
import { BreakReminderSection } from "./BreakReminderSection";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";
import { setupMessages } from "./setup.messages";
import { NotFound } from "@/components/ui/NotFound";
import { cn } from "@/lib/utils/cn";
import { SubjectEmblem } from "@/features/learning/SubjectEmblem";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import type { EnrollmentOverview } from "@/lib/api/types";

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
  const data = child.data;
  const base = `/parent/children/${childId}`;
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-4">
        <BackLink name={data.displayName} />
        <Header child={data} onRenamed={child.refetch} enrollments={enrollments.data} />
      </div>
      <ChildTabs base={base} />
      <Routes>
        <Route
          index
          element={
            // Phone: insights right after the plan - the parent's first question. Desktop: insights
            // ride along in a sticky column beside it.
            <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_340px] lg:gap-x-14">
              <div className="flex min-w-0 flex-col gap-12">
                <PlanAnchor>
                  <LearningNowSection childId={childId} childName={data.displayName} enrollments={enrollments} />
                </PlanAnchor>
                {/* "What is improving?": change over time, right after what is happening now. */}
                <ChangesSection childId={childId} childName={data.displayName} />
              </div>
              <aside className="lg:sticky lg:top-24 lg:self-start">
                <InsightsSection childId={childId} />
              </aside>
            </div>
          }
        />
        <Route
          path="progress"
          element={
            <div className="flex flex-col gap-12">
              <SkillsSection childId={childId} />
              <RecentActivitySection childId={childId} />
            </div>
          }
        />
        <Route
          path="settings"
          element={
            <div className="flex flex-col gap-12">
              {/* Where a parent with a class code looks: it adds the learning a class assigns. */}
              <SchoolAccessSection childId={childId} childName={data.displayName} onJoined={enrollments.refetch} />
              <OwnSpace child={data} />
              <BreakReminderSection childId={childId} childName={data.displayName} />
              <DeleteChildSection child={data} />
            </div>
          }
        />
        <Route path="*" element={<NotFound home={base} homeLabel={data.displayName} />} />
      </Routes>
    </div>
  );
}

/** The child page's sections, as tabs that are links (each section has its own address). */
function ChildTabs({ base }: { base: string }) {
  const m = useMessages(setupMessages).child;
  const tab = ({ isActive }: { isActive: boolean }) =>
    cn(
      "inline-flex h-11 shrink-0 items-center rounded-pill px-5 text-[0.9375rem] font-semibold transition-colors duration-150",
      isActive ? "bg-tonle-900 text-white" : "text-ink-soft hover:bg-white hover:text-tonle-900",
    );
  return (
    <nav aria-label={m.tabs} className="-mx-1 flex gap-1 overflow-x-auto rounded-pill bg-cream-deep/70 p-1 sm:mx-0 sm:w-fit">
      <NavLink to={base} end className={tab}>
        {m.overview}
      </NavLink>
      <NavLink to={`${base}/progress`} className={tab}>
        {m.progress}
      </NavLink>
      <NavLink to={`${base}/settings`} className={tab}>
        {m.settings}
      </NavLink>
    </nav>
  );
}

/** "#plan" (the family home's "Manage subjects") brings the learning plan into view. */
function PlanAnchor({ children }: { children: ReactNode }) {
  const { hash } = useLocation();
  useEffect(() => {
    if (hash === "#plan") document.getElementById("plan")?.scrollIntoView?.({ block: "start" });
  }, [hash]);
  return (
    <div id="plan" className="scroll-mt-24">
      {children}
    </div>
  );
}

/** What belongs to the child, not the parent: their buddy and the language of their space. */
function OwnSpace({ child }: { child: Child }) {
  const m = useMessages(setupMessages).child;
  const b = useMessages(buddyMessages);
  return (
    <section aria-labelledby="own-space" className="flex flex-col gap-4 rounded-[24px] bg-white p-6 shadow-paper ring-1 ring-line/70 sm:p-8">
      <div className="flex items-center gap-4">
        <ChildPortrait buddy={child.learningBuddy} />
        <div>
          <h2 id="own-space" className="font-display text-title font-bold text-tonle-950">
            {m.space}
          </h2>
          <p className="text-[0.9375rem] text-ink-soft">{m.spaceLead(child.displayName)}</p>
        </div>
      </div>
      <ul className="flex flex-wrap gap-2 text-sm font-medium text-ink">
        <li className="rounded-pill bg-paper px-3.5 py-1.5">{m.buddy(b.names[child.learningBuddy])}</li>
        <li className="rounded-pill bg-paper px-3.5 py-1.5">{m.language(child.preferredLanguage === "km" ? "ខ្មែរ" : "English")}</li>
      </ul>
      <Link to={`/learn/${child.id}`} className={buttonClasses("outline", "md", "w-fit")}>
        <ExternalLink size={16} aria-hidden /> {m.openSpace(child.displayName)}
      </Link>
    </section>
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

function Header({ child, onRenamed, enrollments }: { child: Child; onRenamed: () => void; enrollments: EnrollmentOverview[] | null }) {
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
                <h1 className="font-display text-headline font-bold text-tonle-950">
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
        {enrollments && enrollments.length > 0 && (
          <ul className="flex flex-wrap gap-2">
            {enrollments.map((e) => (
              <li key={e.enrollmentId} className="inline-flex items-center gap-2 rounded-pill bg-white/90 py-1 pl-1 pr-3.5 text-sm font-semibold text-tonle-950 ring-1 ring-line/70">
                <SubjectEmblem code={e.curriculum.subjectCode} name={e.curriculum.subjectName} size={28} />
                {e.curriculum.subjectName ?? e.curriculum.name}
              </li>
            ))}
          </ul>
        )}
        <Link to={`/learn/${child.id}`} className={buttonClasses("solid", "lg", "w-full sm:w-auto")}>
          <Play size={18} aria-hidden /> {m.startSession(child.displayName)}
        </Link>
      </header>
    </SceneBand>
  );
}
