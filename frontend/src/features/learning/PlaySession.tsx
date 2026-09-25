import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { X, PartyPopper, Frown, Sparkles, CheckCircle2 } from "lucide-react";
import { enrollmentApi, activityAttemptApi, activityContentApi } from "@/lib/api/endpoints";
import { enrollmentPointerStore } from "@/lib/api/enrollmentPointerStore";
import { ApiError } from "@/lib/api/ApiError";
import { Button } from "@/components/ui/Button";
import { LoadingState, ErrorState, EmptyState } from "@/components/ui/States";
import { QuestionRenderer } from "./QuestionRenderer";
import type { ActivityVersionDetail, AnswerRecord } from "@/lib/api/types";

// Child-facing copy for failures: never the backend's developer-oriented detail.
const LOAD_FAILED = "We couldn't get your activity ready. Let's try again.";
const SAVE_FAILED = "We couldn't save your activity just now. Let's try again.";

type Phase =
  | { kind: "loading" }
  | { kind: "no-enrollment" }
  /** Not an error: LearningPathService#getNextItem (backend) throws a 404
   * ResourceNotFoundException when nothing is PENDING/IN_PROGRESS on the
   * path right now — verified against the Java source. Conflating this
   * with a real failure would show a scary "Try again" loop for a normal,
   * happy state (the child is simply caught up). */
  | { kind: "caught-up" }
  | { kind: "error"; message: string }
  | {
      kind: "playing";
      attemptId: string;
      activity: ActivityVersionDetail;
      index: number;
      feedback: { correct: boolean } | null;
      startedAt: number;
    }
  | { kind: "completing" }
  /** The attempt IS completed and saved; only the adaptive / smart-review
   * follow-up failed. Both endpoints are safe to call again for the same
   * attempt, so this state offers a retry instead of pretending it worked. */
  | { kind: "completed"; score: number | null; hasMore: boolean };

/** The server's recorded answer for this question in this attempt, if any. */
async function findRecordedAnswer(attemptId: string, questionVersionId: string): Promise<AnswerRecord | null> {
  try {
    const answers = await activityAttemptApi.listAnswers(attemptId);
    return answers.find((answer) => answer.questionVersionId === questionVersionId) ?? null;
  } catch {
    return null;
  }
}

export function PlaySession() {
  const { childId } = useParams<{ childId: string }>();
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  // Guards against React 18 StrictMode's dev-only double-invoke of effects
  // (mount -> unmount -> mount), which would otherwise be able to fire
  // activityAttemptApi.start() twice in a row for the same
  // learningPathItemId and create two ActivityAttempt rows for one item.
  const loadInFlight = useRef(false);

  /** "Has more" = the path still has PENDING/IN_PROGRESS work, exactly what the
   * backend's next-activity endpoint answers (404 = nothing left right now).
   * Not whether adaptive/review happened to insert something: pending
   * curriculum items count too. If the check itself fails we cannot know, so
   * offer "Keep going" rather than falsely telling the child they are done. */
  const hasPendingWork = useCallback(async () => {
    const enrollmentId = childId ? enrollmentPointerStore.get(childId) : null;
    if (!enrollmentId) return false;
    try {
      await enrollmentApi.nextActivity(enrollmentId);
      return true;
    } catch (err) {
      return !(err instanceof ApiError && err.status === 404);
    }
  }, [childId]);

  /** Completion is the whole learning-state transition: by the time complete
   * returns, the backend has recorded evidence and made the Adaptive Engine and
   * Smart Review decisions in the same transaction (Battle 5D), so there is no
   * follow-up call whose failure could leave a completed activity without its
   * next steps. All that's left is to read whether the path has more. */
  const showCompleted = useCallback(
    async (score: number | null) => {
      setPhase({ kind: "completed", score, hasMore: await hasPendingWork() });
    },
    [hasPendingWork],
  );

  const finishAttempt = useCallback(
    async (attemptId: string) => {
      setPhase({ kind: "completing" });
      let score: number | null;
      try {
        score = (await activityAttemptApi.complete(attemptId)).score;
      } catch {
        // Retry goes through loadNext, which re-reads the attempt from the
        // server: it resumes at any question the server says is unanswered, or
        // moves on if the attempt did complete.
        setPhase({ kind: "error", message: SAVE_FAILED });
        return;
      }
      await showCompleted(score);
    },
    [showCompleted],
  );

  const loadNext = useCallback(async () => {
    if (!childId || loadInFlight.current) return;
    loadInFlight.current = true;
    setPhase({ kind: "loading" });

    try {
      const enrollmentId = enrollmentPointerStore.get(childId);
      if (!enrollmentId) {
        setPhase({ kind: "no-enrollment" });
        return;
      }

      let item;
      try {
        item = await enrollmentApi.nextActivity(enrollmentId);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          setPhase({ kind: "caught-up" });
          return;
        }
        setPhase({ kind: "error", message: LOAD_FAILED });
        return;
      }

      try {
        // Returns the existing IN_PROGRESS attempt for this path item if there
        // is one (backend Battle 2A) — i.e. this is also the resume path.
        const attempt = await activityAttemptApi.start(childId, {
          activityVersionId: item.activityVersionId,
          learningPathItemId: item.id,
        });
        const [activity, answers] = await Promise.all([
          activityContentApi.getVersion(item.activityVersionId),
          activityAttemptApi.listAnswers(attempt.id),
        ]);

        // Resume from the server's persisted state, not local UI state: the
        // first question (in the activity's own order) with no recorded answer.
        const items = [...activity.items].sort((a, b) => a.sequenceOrder - b.sequenceOrder);
        const answered = new Set(answers.map((answer) => answer.questionVersionId));
        const resumeIndex = items.findIndex((question) => !answered.has(question.questionVersionId));

        if (resumeIndex === -1) {
          // Every question already answered (e.g. the child left on the last
          // feedback screen) — the attempt only needs completing.
          await finishAttempt(attempt.id);
          return;
        }

        setPhase({
          kind: "playing",
          attemptId: attempt.id,
          activity: { ...activity, items },
          index: resumeIndex,
          feedback: null,
          startedAt: Date.now(),
        });
      } catch {
        setPhase({ kind: "error", message: LOAD_FAILED });
      }
    } finally {
      loadInFlight.current = false;
    }
  }, [childId, finishAttempt]);

  useEffect(() => {
    void loadNext();
  }, [loadNext]);

  if (!childId) return null;

  if (phase.kind === "loading") {
    return (
      <PlayShell childId={childId}>
        <LoadingState label="Getting your next activity ready…" />
      </PlayShell>
    );
  }

  if (phase.kind === "no-enrollment") {
    return (
      <PlayShell childId={childId}>
        <EmptyState
          title="No learning path chosen yet"
          description="Head back and pick a learning path for this child first."
          action={
            <Link to={`/parent/children/${childId}`}>
              <Button size="md" className="mt-2">
                Back to overview
              </Button>
            </Link>
          }
        />
      </PlayShell>
    );
  }

  if (phase.kind === "caught-up") {
    return (
      <PlayShell childId={childId}>
        <div className="flex flex-col items-center gap-4 rounded-[32px] border-[6px] border-ink bg-white p-10 text-center shadow-soft">
          <CheckCircle2 size={48} className="text-brand-green" />
          <p className="text-2xl font-extrabold text-ink">All caught up!</p>
          <p className="text-muted">Nothing new to work on right now — check back soon.</p>
          <Link to={`/parent/children/${childId}`}>
            <Button size="lg" className="mt-2">
              Back to overview
            </Button>
          </Link>
        </div>
      </PlayShell>
    );
  }

  if (phase.kind === "error") {
    return (
      <PlayShell childId={childId}>
        <ErrorState message={phase.message} onRetry={loadNext} />
      </PlayShell>
    );
  }

  if (phase.kind === "completing") {
    return (
      <PlayShell childId={childId}>
        <LoadingState label="Saving progress…" />
      </PlayShell>
    );
  }

  if (phase.kind === "completed") {
    return (
      <PlayShell childId={childId}>
        <CompletionScreen score={phase.score} hasMore={phase.hasMore} childId={childId} onKeepGoing={loadNext} />
      </PlayShell>
    );
  }

  return (
    <PlayShell childId={childId}>
      {/* Keyed per question: each question gets fresh interaction state
          (submitting flag, error, idempotency keys) instead of inheriting the
          previous question's — a still-"submitting" flag used to leave every
          question after the first permanently disabled. */}
      <ActivityQuestion
        key={`${phase.attemptId}:${phase.index}`}
        attemptId={phase.attemptId}
        activity={phase.activity}
        index={phase.index}
        feedback={phase.feedback}
        startedAt={phase.startedAt}
        onAnswered={(correct) => setPhase({ ...phase, feedback: { correct } })}
        onResync={loadNext}
        onContinue={() => {
          const nextIndex = phase.index + 1;
          if (nextIndex < phase.activity.items.length) {
            setPhase({ ...phase, index: nextIndex, feedback: null, startedAt: Date.now() });
            return;
          }
          void finishAttempt(phase.attemptId);
        }}
      />
    </PlayShell>
  );
}

function PlayShell({ childId, children }: { childId: string; children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-hero px-4 py-8 sm:px-8">
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <Link
          to={`/parent/children/${childId}`}
          className="flex w-fit items-center gap-2 rounded-pill bg-white px-4 py-2 text-sm font-semibold text-muted shadow-tight hover:text-ink"
        >
          <X size={16} /> Exit
        </Link>
        {children}
      </div>
    </div>
  );
}

function ActivityQuestion({
  attemptId,
  activity,
  index,
  feedback,
  startedAt,
  onAnswered,
  onResync,
  onContinue,
}: {
  attemptId: string;
  activity: ActivityVersionDetail;
  index: number;
  feedback: { correct: boolean } | null;
  startedAt: number;
  onAnswered: (correct: boolean) => void;
  onResync: () => void;
  onContinue: () => void;
}) {
  const item = activity.items[index];
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // One idempotency key per distinct answer to this question: retrying the
  // same answer after a lost response replays the original result server-side
  // instead of conflicting; choosing a different answer gets a fresh key.
  const idempotencyKeys = useRef(new Map<string, string>());

  if (!item) return null;

  async function handleAnswer(submittedAnswer: string) {
    if (isSubmitting) return; // guards against a rapid double-tap firing two submits
    setIsSubmitting(true);
    setSubmitError(null);

    let idempotencyKey = idempotencyKeys.current.get(submittedAnswer);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      idempotencyKeys.current.set(submittedAnswer, idempotencyKey);
    }

    try {
      const result = await activityAttemptApi.submitAnswer(attemptId, {
        questionVersionId: item.questionVersionId,
        submittedAnswer,
        timeSpentSeconds: Math.max(1, Math.round((Date.now() - startedAt) / 1000)),
        idempotencyKey,
      });
      onAnswered(result.correct);
      return;
    } catch (err) {
      // The server is the source of truth: the answer may already be recorded
      // (lost response, answered in another tab) — if so, carry on with it.
      const recorded = await findRecordedAnswer(attemptId, item.questionVersionId);
      if (recorded) {
        onAnswered(recorded.correct);
        return;
      }
      if (err instanceof ApiError && err.status === 409) {
        // The attempt no longer accepts this answer (e.g. completed or
        // abandoned elsewhere) — reload the real state rather than guess.
        onResync();
        return;
      }
      setSubmitError(
        err instanceof ApiError && err.status === 501
          ? "This question can't be answered right now. Tap Exit to go back for now."
          : "We couldn't send that answer. Please try again.",
      );
      setIsSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6 rounded-[32px] border-[6px] border-ink bg-white p-6 shadow-soft sm:p-10">
      <div className="flex items-center justify-between">
        <p className="text-sm font-bold text-muted">{activity.title}</p>
        <div className="flex gap-1.5" aria-label={`Question ${index + 1} of ${activity.items.length}`}>
          {activity.items.map((_, i) => (
            <span
              key={i}
              className={`h-2 w-6 rounded-full ${i < index ? "bg-brand-green" : i === index ? "bg-brand-blue" : "bg-hairline"}`}
            />
          ))}
        </div>
      </div>

      <p className="text-2xl font-extrabold leading-snug text-ink">{item.prompt}</p>

      {feedback === null ? (
        <>
          <QuestionRenderer item={item} onAnswer={handleAnswer} disabled={isSubmitting} />
          {submitError && (
            <p role="alert" className="text-sm font-semibold text-brand-red">
              {submitError}
            </p>
          )}
        </>
      ) : (
        <FeedbackPanel correct={feedback.correct} onContinue={onContinue} />
      )}
    </div>
  );
}

function FeedbackPanel({ correct, onContinue }: { correct: boolean; onContinue: () => void }) {
  return (
    <div
      role="status"
      className={`flex flex-col items-center gap-4 rounded-3xl p-8 text-center ${correct ? "bg-brand-greenTint" : "bg-brand-amberTint"}`}
    >
      {correct ? <PartyPopper size={40} className="text-brand-green" /> : <Frown size={40} className="text-brand-amber" />}
      <p className="text-xl font-extrabold text-ink">{correct ? "Nice work!" : "Good try!"}</p>
      <p className="text-sm text-muted">{correct ? "You got it right." : "That wasn't quite it — let's keep going."}</p>
      <Button size="lg" onClick={onContinue}>
        Continue
      </Button>
    </div>
  );
}

function CompletionScreen({
  score,
  hasMore,
  childId,
  onKeepGoing,
}: {
  score: number | null;
  hasMore: boolean;
  childId: string;
  onKeepGoing: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-6 rounded-[32px] border-[6px] border-ink bg-white p-10 text-center shadow-soft">
      <Sparkles size={48} className="text-brand-amber" />
      <div>
        <p className="text-2xl font-extrabold text-ink">Activity complete!</p>
        {score !== null && <p className="mt-1 text-muted">Score: {Math.round(score)}%</p>}
      </div>
      <div className="flex gap-3">
        {hasMore && (
          <Button size="lg" onClick={onKeepGoing}>
            Keep Going
          </Button>
        )}
        <Link to={`/parent/children/${childId}`}>
          <Button size="lg" variant={hasMore ? "outline" : "solid"}>
            Done for now
          </Button>
        </Link>
      </div>
    </div>
  );
}
