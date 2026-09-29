import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Plus } from "lucide-react";
import { studioApi, type LessonSummary } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { Panel, SkillChips, StatusBadge } from "./shared";
import { currentStatus, errorText, useStudio } from "./studioLogic";

/** Lessons group activities; an activity is what a learning path places and a child plays. */
export function ActivitiesPage() {
  const { catalog } = useStudio();
  const lessons = useAsync(() => studioApi.lessons(), []);
  const subjectLessons = (lessons.data ?? []).filter((l) => l.subjectId === catalog.subject.id);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div className="flex flex-col gap-6">
        {lessons.isLoading && !lessons.data && <LoadingState label="Loading activities…" />}
        {lessons.error && <ErrorState error={lessons.error} onRetry={lessons.refetch} />}
        {lessons.data && subjectLessons.length === 0 && (
          <EmptyState title="No lessons yet" description="Add a lesson, then build activities inside it." />
        )}
        {subjectLessons.map((lesson) => (
          <LessonPanel key={lesson.id} lesson={lesson} />
        ))}
      </div>
      <NewLesson onCreated={lessons.refetch} />
    </div>
  );
}

function LessonPanel({ lesson }: { lesson: LessonSummary }) {
  const { catalog } = useStudio();
  const [adding, setAdding] = useState(false);
  return (
    <Panel
      title={lesson.title}
      action={
        !adding && (
          <Button variant="outline" className="px-4 py-2" onClick={() => setAdding(true)} disabled={catalog.skills.length === 0}>
            <Plus size={16} aria-hidden /> Activity
          </Button>
        )
      }
    >
      {adding && <NewActivity lessonId={lesson.id} onCancel={() => setAdding(false)} />}
      {lesson.activities.length === 0 && !adding && <p className="text-sm text-ink-soft">No activities in this lesson yet.</p>}
      <ul className="flex flex-col divide-y divide-line">
        {lesson.activities.map((activity) => {
          const status = currentStatus(activity.versions);
          return (
            <li key={activity.id}>
              <Link
                to={activity.id}
                className="flex items-center justify-between gap-3 rounded-[10px] px-2 py-3 hover:bg-paper focus-visible:bg-paper"
              >
                <span className="flex min-w-0 flex-col gap-1.5">
                  <span className="truncate font-semibold text-ink">{activity.title}</span>
                  <SkillChips skillIds={activity.skillIds} catalog={catalog} />
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="hidden text-xs text-ink-soft sm:inline">{status.label}</span>
                  {status.status && <StatusBadge status={status.status} />}
                  <ChevronRight size={18} className="text-ink-soft" aria-hidden />
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

function NewActivity({ lessonId, onCancel }: { lessonId: string; onCancel: () => void }) {
  const { catalog } = useStudio();
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [skillIds, setSkillIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const activity = await studioApi.createActivity(lessonId, { title: title.trim(), skillIds });
      navigate(activity.id);
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="mb-4 flex flex-col gap-4 rounded-[10px] bg-paper p-4">
      <FormField label="Activity title" value={title} maxLength={200} required onChange={(e) => setTitle(e.target.value)} />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-semibold text-ink">Skills learning paths should choose it for</legend>
        {catalog.skills.map((skill) => (
          <label key={skill.id} className="flex items-center gap-2 text-sm text-ink">
            <input
              type="checkbox"
              className="h-4 w-4 accent-tonle-700"
              checked={skillIds.includes(skill.id)}
              onChange={(e) => setSkillIds(e.target.checked ? [...skillIds, skill.id] : skillIds.filter((id) => id !== skill.id))}
            />
            {skill.name}
          </label>
        ))}
      </fieldset>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy || !title.trim() || skillIds.length === 0}>
          {busy ? "Creating…" : "Create activity"}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

function NewLesson({ onCreated }: { onCreated: () => void }) {
  const { catalog } = useStudio();
  const [title, setTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await studioApi.createLesson({ subjectId: catalog.subject.id, title: title.trim() });
      setTitle("");
      onCreated();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel title="Add a lesson" className="self-start">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="Lesson title" value={title} maxLength={200} required onChange={(e) => setTitle(e.target.value)} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={busy || !title.trim()} className="w-fit">
          <Plus size={16} aria-hidden /> {busy ? "Adding…" : "Add lesson"}
        </Button>
      </form>
    </Panel>
  );
}
