import { useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, MessageSquare, Send, Undo2 } from "lucide-react";
import { reviewApi, type ReviewKind, type ReviewState } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ApiError } from "@/lib/api/ApiError";
import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Panel, TextAreaField } from "./shared";
import { errorText, useStudioScope } from "./studioLogic";
import { useMessages } from "@/i18n/catalog";
import { formatDate } from "@/i18n/format";
import { studioMessages } from "./messages";

const day = (iso: string) => formatDate(iso, { day: "numeric", month: "short", year: "numeric" });

/**
 * Publishing a draft (Y14). Without review switched on, or for a teacher's own content, it is the
 * Publish button as before. With review on, platform content is published by a second
 * administrator: the author asks for a review; another administrator approves it - which
 * publishes it - or asks for changes, which the author reads here.
 */
export function PublishControl({
  kind,
  versionId,
  canPublish,
  busy,
  onPublish,
  onPublished,
  size = "md",
}: {
  kind: ReviewKind;
  versionId: string;
  canPublish: boolean;
  busy: boolean;
  onPublish: () => void;
  onPublished: () => void;
  size?: "sm" | "md";
}) {
  const state = useAsync(() => reviewApi.state(kind, versionId), [kind, versionId]);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [noting, setNoting] = useState(false);
  const [note, setNote] = useState("");
  const m = useMessages(studioMessages).review;

  async function act(call: () => Promise<ReviewState>, published = false) {
    setWorking(true);
    setError(null);
    try {
      await call();
      setNoting(false);
      setNote("");
      state.refetch();
      if (published) onPublished();
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 422
          ? m.fixFirst
          : errorText(err, m.failed),
      );
      state.refetch();
    } finally {
      setWorking(false);
    }
  }

  // Until the review state is known, publishing waits rather than guessing.
  if (!state.data) {
    return state.error ? (
      <Button size={size} disabled>
        <Send size={15} aria-hidden /> {m.publish}
      </Button>
    ) : null;
  }
  if (!state.data.required) {
    return (
      <Button size={size} disabled={busy || !canPublish} onClick={onPublish}>
        <Send size={15} aria-hidden /> {m.publish}
      </Button>
    );
  }

  const review = state.data.review;
  const open = review?.status === "OPEN" ? review : null;
  return (
    <div className="flex w-full flex-col items-end gap-2">
      {open && open.mine && (
        <>
          <p role="status" className="text-right text-sm text-ink-soft">
            {m.askedMine(day(open.requestedAt))}
            {open.changedSince && ` ${m.changedMine}`}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            {open.changedSince && (
              <Button
                size={size}
                disabled={working || busy || !canPublish}
                onClick={() => act(() => reviewApi.request(kind, versionId))}
              >
                <Send size={15} aria-hidden /> {m.askAgain}
              </Button>
            )}
            <Button size={size} variant="ghost" disabled={working} onClick={() => act(() => reviewApi.withdraw(kind, versionId))}>
              <Undo2 size={15} aria-hidden /> {m.withdraw}
            </Button>
          </div>
        </>
      )}
      {open && !open.mine && (
        <>
          <p role="status" className="text-right text-sm text-ink-soft">
            {m.askedOther(open.requestedBy, day(open.requestedAt))}
            {open.changedSince && ` ${m.changedOther}`}
          </p>
          {noting ? (
            <div className="flex w-full flex-col gap-2" role="group" aria-label={m.askForChanges}>
              <TextAreaField label={m.whatShouldChange} value={note} maxLength={1000} onChange={(e) => setNote(e.target.value)} />
              <div className="flex flex-wrap justify-end gap-2">
                <Button
                  size={size}
                  disabled={working || !note.trim()}
                  onClick={() => act(() => reviewApi.requestChanges(open.id, note.trim()))}
                >
                  <MessageSquare size={15} aria-hidden /> {m.send}
                </Button>
                <Button size={size} variant="ghost" disabled={working} onClick={() => setNoting(false)}>
                  {m.cancel}
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap justify-end gap-2">
              <Button size={size} variant="outline" disabled={working} onClick={() => setNoting(true)}>
                <MessageSquare size={15} aria-hidden /> {m.askForChangesMenu}
              </Button>
              <Button
                size={size}
                disabled={working || busy || !canPublish || open.changedSince}
                onClick={() => act(() => reviewApi.approve(open.id), true)}
              >
                <CheckCircle2 size={15} aria-hidden /> {m.approve}
              </Button>
            </div>
          )}
        </>
      )}
      {!open && (
        <>
          {review?.status === "CHANGES_REQUESTED" && (
            <p role="status" className="w-full rounded-[10px] bg-light-soft/60 px-3 py-2 text-sm text-ink ring-1 ring-light/40">
              <span className="font-semibold">{m.changesAsked(review.decidedBy)}</span> {review.note ?? m.noNote}
            </p>
          )}
          <p className="text-right text-sm text-ink-soft">
            {m.needsApproval}
          </p>
          <Button
            size={size}
            disabled={working || busy || !canPublish}
            onClick={() => act(() => reviewApi.request(kind, versionId))}
          >
            <Send size={15} aria-hidden /> {m.askForReview}
          </Button>
        </>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </div>
  );
}

const KIND_PATH: Record<ReviewKind, string> = { QUESTION: "questions", ACTIVITY: "activities", CURRICULUM: "curricula" };

/** Drafts waiting for a second administrator's review (shown only while review is switched on). */
export function ReviewQueue() {
  const { base } = useStudioScope();
  const settings = useAsync(() => reviewApi.settings(), []);
  const queue = useAsync(() => (settings.data?.required ? reviewApi.queue() : Promise.resolve(null)), [settings.data?.required]);
  const m = useMessages(studioMessages).review;
  if (!settings.data?.required) return null;
  const rows = queue.data?.items ?? [];
  return (
    <div id="reviews">
      <Panel title={m.queueTitle}>
        {queue.isLoading && !queue.data && <LoadingState label={m.loading} />}
        {queue.error && <ErrorState error={queue.error} onRetry={queue.refetch} />}
        {queue.data && rows.length === 0 && <p className="text-sm text-ink-soft">{m.nothing}</p>}
        {rows.length > 0 && (
          <ul aria-label={m.list} className="flex flex-col divide-y divide-line border-y border-line">
            {rows.map((row) => (
              <li key={row.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-3">
                <span className="min-w-0">
                  <span className="block font-medium text-ink">
                    {m.kinds[row.kind]}: {row.title}
                  </span>
                  <span className="block text-sm text-ink-soft [overflow-wrap:anywhere]">
                    {m.askedBy(row.requestedBy, day(row.requestedAt))}
                  </span>
                </span>
                <Link
                  to={`${base}/studio/${row.subjectId}/${KIND_PATH[row.kind]}/${row.contentId}`}
                  className="text-sm font-semibold text-tonle-700 hover:text-tonle-900"
                >
                  {m.open}
                </Link>
              </li>
            ))}
          </ul>
        )}
        {queue.data && queue.data.total > rows.length && (
          <p className="mt-2 text-sm text-ink-soft">
            {m.oldest(rows.length, queue.data.total)}
          </p>
        )}
      </Panel>
    </div>
  );
}
