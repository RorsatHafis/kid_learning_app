import { useState } from "react";
import { Copy, Users } from "lucide-react";
import { studioApi, type LessonSummary, type LessonVisibility } from "@/lib/api/studio";
import { ApiError } from "@/lib/api/ApiError";
import { useAsync } from "@/lib/hooks/useAsync";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useMessages } from "@/i18n/catalog";
import { sharingMessages } from "./sharing.messages";

/**
 * M5b: who besides its author can find a teacher's lesson - only them, or the teachers of their school, who can copy
 * it into their own lessons. The server decides who those are (staff of an open, paid school).
 */
export function LessonVisibilityControl({ lesson, onChanged }: { lesson: LessonSummary; onChanged: () => void }) {
  const m = useMessages(sharingMessages);
  const [value, setValue] = useState<LessonVisibility>(lesson.visibility ?? "PRIVATE");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function change(next: LessonVisibility) {
    const before = value;
    setValue(next);
    setBusy(true);
    setError(null);
    try {
      await studioApi.setLessonVisibility(lesson.id, next);
      onChanged();
    } catch (err) {
      setValue(before);
      setError(err instanceof ApiError && err.problem?.detail && err.status !== 500 ? err.problem.detail : m.saveFailed);
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="flex flex-col items-end gap-1">
      <span className="flex items-center gap-1.5 text-xs font-semibold text-ink-soft">
        <Users size={14} aria-hidden />
        <select
          aria-label={m.whoFinds(lesson.title)}
          value={value}
          disabled={busy}
          onChange={(e) => void change(e.target.value as LessonVisibility)}
          className="h-9 rounded-[10px] border border-line-strong bg-white px-2 text-sm text-ink disabled:opacity-60"
        >
          <option value="PRIVATE">{m.onlyYou}</option>
          <option value="SCHOOL">{m.school}</option>
        </select>
      </span>
      {error && (
        <span role="alert" className="max-w-[16rem] text-right text-xs font-medium text-brick-deep">
          {error}
        </span>
      )}
    </span>
  );
}

/** The badge on a lesson copied from a colleague's shared lesson. */
export function CopiedBadge() {
  const m = useMessages(sharingMessages);
  return (
    <span title={m.copiedTitle} className="rounded-full bg-tonle-50 px-2 py-0.5 text-xs font-semibold text-tonle-700">
      {m.copied}
      <span className="sr-only">: {m.copiedTitle}</span>
    </span>
  );
}

/** M5b: colleagues' shared lessons in this subject, each copied into the teacher's own lessons on request. */
export function SharedByColleagues({ subjectId, onCopied }: { subjectId: string; onCopied: () => void }) {
  const m = useMessages(sharingMessages);
  const shared = useAsync(() => studioApi.sharedLessons(subjectId), [subjectId]);
  const [copying, setCopying] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: "status" | "alert"; text: string } | null>(null);

  async function copy(id: string, title: string) {
    setCopying(id);
    setMessage(null);
    try {
      await studioApi.copySharedLesson(id);
      setMessage({ tone: "status", text: m.copiedInto(title) });
      onCopied();
    } catch (err) {
      const text =
        err instanceof ApiError && err.status === 404
          ? m.gone
          : err instanceof ApiError && (err.status === 409 || err.status === 422) && err.problem?.detail
            ? err.problem.detail
            : m.failed;
      setMessage({ tone: "alert", text });
      if (err instanceof ApiError && err.status === 404) shared.refetch();
    } finally {
      setCopying(null);
    }
  }

  if (shared.isLoading && !shared.data) return <LoadingState label={m.loading} />;
  if (shared.error) return <ErrorState error={shared.error} onRetry={shared.refetch} />;
  if (!shared.data || (shared.data.length === 0 && !message)) return null;

  return (
    <section aria-labelledby="shared-by-colleagues" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <h2 id="shared-by-colleagues" className="font-display text-[1.2rem] font-bold text-tonle-950">
          {m.title}
        </h2>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-soft">{m.lead}</p>
      </div>
      {message && (
        <p
          role={message.tone}
          className={
            message.tone === "alert"
              ? "rounded-[10px] bg-brick-soft px-4 py-3 text-sm font-medium text-brick-deep"
              : "rounded-[10px] bg-palm-soft px-4 py-3 text-sm font-medium text-palm-deep"
          }
        >
          {message.text}
        </p>
      )}
      <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[20px] bg-white shadow-card">
        {shared.data.map((lesson) => (
          <li key={lesson.id} className="flex flex-col gap-3 p-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex min-w-0 flex-col gap-1">
              <h3 className="font-semibold text-ink">{lesson.title}</h3>
              <p className="text-xs text-ink-faint">{m.sharedAt(lesson.schools.join(", "))}</p>
              <ul className="mt-1 flex flex-col gap-0.5 text-sm text-ink-soft">
                {lesson.activities.map((activity) => (
                  <li key={activity.id}>
                    {activity.title} · {m.questions(activity.questions)}
                  </li>
                ))}
              </ul>
            </div>
            <Button
              variant="outline"
              size="sm"
              className="shrink-0 self-start"
              disabled={copying !== null}
              onClick={() => void copy(lesson.id, lesson.title)}
            >
              <Copy size={15} aria-hidden /> {copying === lesson.id ? m.copying : m.copy}
            </Button>
          </li>
        ))}
      </ul>
    </section>
  );
}
