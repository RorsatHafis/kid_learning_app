/**
 * A child's drawings for the grown-ups who may see them (owner-approved 2026-11-10): each drawing as the child made it
 * on the guide they had, with the feedback left on it, and a box for a few words. Used by the family's progress page
 * and by a class's page (the server decides what each may see and which feedback shows).
 */
import { useState, type FormEvent } from "react";
import { useAsync } from "@/lib/hooks/useAsync";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { textareaClasses } from "@/features/auth/FormField";
import { problemMessage } from "@/lib/api/problemMessage";
import { formatDate } from "@/i18n/format";
import { useMessages } from "@/i18n/catalog";
import { DrawingCanvas } from "./DrawingCanvas";
import { strokesOf } from "./drawing";
import { drawingMessages } from "./drawing.messages";
import type { ReviewDrawing } from "@/lib/api/drawings";

export function DrawingsReview({
  load,
  send,
  childName,
  showChild,
}: {
  load: () => Promise<ReviewDrawing[]>;
  send: (drawing: ReviewDrawing, body: string) => Promise<ReviewDrawing>;
  /** The child, for a family page; a class page names each drawing's child instead. */
  childName?: string;
  showChild?: boolean;
}) {
  const m = useMessages(drawingMessages);
  const drawings = useAsync(load, []);
  if (drawings.isLoading && !drawings.data) return <LoadingState label={m.loading} />;
  if (drawings.error) return <ErrorState error={drawings.error} onRetry={drawings.refetch} />;
  const list = drawings.data ?? [];
  if (list.length === 0) return <p className="rounded-[18px] bg-cream p-5 text-ink-soft">{m.noDrawings(childName ?? "")}</p>;
  return (
    <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2">
      {list.map((drawing) => (
        <li key={drawing.answerId}>
          <DrawingCard drawing={drawing} send={send} showChild={showChild} />
        </li>
      ))}
    </ul>
  );
}

function DrawingCard({
  drawing: initial,
  send,
  showChild,
}: {
  drawing: ReviewDrawing;
  send: (drawing: ReviewDrawing, body: string) => Promise<ReviewDrawing>;
  showChild?: boolean;
}) {
  const m = useMessages(drawingMessages);
  const [drawing, setDrawing] = useState(initial);
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fieldId = `feedback-${drawing.answerId}`;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!body.trim()) return;
    setBusy(true);
    setError(null);
    try {
      setDrawing(await send(drawing, body.trim()));
      setBody("");
    } catch (err) {
      setError(problemMessage(err, m.sending));
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="flex h-full flex-col gap-4 rounded-[22px] bg-white p-5 shadow-card">
      <header>
        <h3 className="font-display text-lg font-bold text-tonle-950 [overflow-wrap:anywhere]">{drawing.prompt}</h3>
        <p className="text-sm text-ink-soft">
          {[showChild ? drawing.childName : null, drawing.activityTitle, formatDate(drawing.drawnAt)].filter(Boolean).join(" · ")}
        </p>
      </header>
      <DrawingCanvas guide={drawing.guide} strokes={strokesOf(drawing.strokes)} readOnly label={`${m.yourDrawing}: ${drawing.prompt}`} className="max-w-[18rem]" />
      {drawing.feedback.length > 0 && (
        <ul className="flex flex-col gap-2">
          {drawing.feedback.map((f) => (
            <li key={f.id} className="rounded-[12px] bg-paper px-3 py-2 text-sm text-ink ring-1 ring-inset ring-line">
              <span className="font-semibold">{f.mine ? m.you : f.side === "SCHOOL" ? m.feedbackSchool : m.feedbackFamily}: </span>
              <span className="[overflow-wrap:anywhere]">{f.body}</span>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={(e) => void submit(e)} className="mt-auto flex flex-col gap-2">
        <label htmlFor={fieldId} className="text-sm font-semibold text-ink">
          {m.feedbackLabel}
        </label>
        <textarea id={fieldId} value={body} maxLength={500} rows={2} onChange={(e) => setBody(e.target.value)} className={textareaClasses} />
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" size="sm" disabled={busy || !body.trim()} className="w-fit">
          {busy ? m.sending : m.send}
        </Button>
      </form>
    </article>
  );
}
